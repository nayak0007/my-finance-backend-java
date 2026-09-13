package com.finance.tracker.security;

import com.finance.tracker.config.AppProperties;
import com.finance.tracker.exception.AppException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.proc.SingleKeyJWSKeySelector;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /**
     * Signature algorithms this API accepts on inbound access tokens. Neon Auth signs
     * with EdDSA (Ed25519) and publishes the matching key at
     * {@code <NEON_AUTH_URL>/.well-known/jwks.json}; HS256 stays for locally minted test
     * tokens. Everything else — including an unsecured "none" token — is rejected.
     */
    private static final Set<JWSAlgorithm> TRUSTED_ALGORITHMS = Set.of(
            JWSAlgorithm.EdDSA,
            JWSAlgorithm.RS256,
            JWSAlgorithm.ES256,
            JWSAlgorithm.HS256);

    private final AppProperties props;

    /**
     * One processor per signing algorithm. The token's own header decides which is used,
     * so a stray NEON_JWT_SECRET cannot break verification of Neon's EdDSA tokens (and a
     * forged HS256 token can never be validated against a JWKS public key).
     */
    private final Map<String, ConfigurableJWTProcessor<SecurityContext>> processors = new ConcurrentHashMap<>();

    /** Shared so the JWKS key cache is reused by every non-HMAC processor. */
    private volatile JWKSource<SecurityContext> jwks;

    public JwtService(AppProperties props) {
        this.props = props;
    }

    public AuthUser verify(String token) {
        try {
            JWSAlgorithm algorithm = algorithmOf(token);
            JWTClaimsSet claims = processor(algorithm).process(token, null);
            String sub = claims.getSubject();
            if (sub == null || sub.isBlank()) {
                sub = claims.getStringClaim("id");
            }
            if (sub == null || sub.isBlank()) {
                throw AppException.unauthorized("Token is missing subject");
            }
            String email = claims.getStringClaim("email");
            String role = claims.getStringClaim("role");
            log.debug("jwt claims sub={} email={} role={} exp={}", sub, email, role, claims.getExpirationTime());
            return new AuthUser(UUID.fromString(sub), email, role);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.warn("jwt verify failed: {}", e.getMessage());
            throw AppException.unauthorized();
        }
    }

    /**
     * Reads the signing algorithm from the (unverified) JWS header. Only trusted
     * algorithms go any further, which keeps the transport's choice of verifier from
     * being attacker-controlled in an interesting way.
     */
    private JWSAlgorithm algorithmOf(String token) {
        JWSAlgorithm algorithm;
        try {
            algorithm = JWSObject.parse(token).getHeader().getAlgorithm();
        } catch (Exception e) {
            log.warn("jwt verify failed: token is not a signed JWT");
            throw AppException.unauthorized();
        }
        if (algorithm == null || !TRUSTED_ALGORITHMS.contains(algorithm)) {
            log.warn("jwt verify failed: unsupported signing algorithm {}", algorithm == null ? "none" : algorithm.getName());
            throw AppException.unauthorized("Unsupported token signing algorithm");
        }
        return algorithm;
    }

    private ConfigurableJWTProcessor<SecurityContext> processor(JWSAlgorithm algorithm) {
        ConfigurableJWTProcessor<SecurityContext> cached = processors.get(algorithm.getName());
        if (cached != null) {
            return cached;
        }
        ConfigurableJWTProcessor<SecurityContext> built = buildProcessor(algorithm);
        ConfigurableJWTProcessor<SecurityContext> existing = processors.putIfAbsent(algorithm.getName(), built);
        return existing != null ? existing : built;
    }

    private ConfigurableJWTProcessor<SecurityContext> buildProcessor(JWSAlgorithm algorithm) {
        DefaultJWTProcessor<SecurityContext> p = new DefaultJWTProcessor<>();
        if (JWSAlgorithm.HS256.equals(algorithm)) {
            String secret = props.getNeon().getJwtSecret();
            if (secret == null || secret.isBlank()) {
                // The API never mints HS256 tokens itself, so an HMAC token we cannot
                // verify with a configured secret is either forged or a config slip.
                log.error("jwt verify failed: token is HS256-signed but NEON_JWT_SECRET is not set");
                throw AppException.unauthorized();
            }
            log.info("jwt verifier alg=HS256 secretChars={}", secret.length());
            SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            p.setJWSKeySelector(new SingleKeyJWSKeySelector<>(JWSAlgorithm.HS256, key));
            return p;
        }
        log.info("jwt verifier alg={} jwks={}", algorithm.getName(), jwksUrl());
        p.setJWSKeySelector(new JWSVerificationKeySelector<>(algorithm, jwksSource()));
        return p;
    }

    private String jwksUrl() {
        String base = props.getNeon().getAuthUrl();
        if (base == null || base.isBlank()) {
            log.error("jwt verify failed: token needs the JWKS at NEON_AUTH_URL/.well-known/jwks.json but NEON_AUTH_URL is not set");
            throw AppException.upstream("Identity provider is not configured");
        }
        return base.replaceAll("/$", "") + "/.well-known/jwks.json";
    }

    private JWKSource<SecurityContext> jwksSource() {
        JWKSource<SecurityContext> source = jwks;
        if (source == null) {
            try {
                source = new RemoteJWKSet<>(URI.create(jwksUrl()).toURL());
            } catch (Exception e) {
                log.error("jwt verify failed: cannot build JWKS source: {}", e.toString());
                throw AppException.upstream("Identity provider is not configured");
            }
            jwks = source;
        }
        return source;
    }
}
