package com.finance.tracker.security;

import com.finance.tracker.config.AppProperties;
import com.finance.tracker.exception.AppException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.proc.SingleKeyJWSKeySelector;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class JwtService {

    private final AppProperties props;
    private ConfigurableJWTProcessor<SecurityContext> processor;

    public JwtService(AppProperties props) {
        this.props = props;
    }

    public AuthUser verify(String token) {
        try {
            JWTClaimsSet claims = processor().process(token, null);
            String sub = claims.getSubject();
            if (sub == null || sub.isBlank()) {
                throw AppException.unauthorized("Token is missing subject");
            }
            String email = claims.getStringClaim("email");
            String role = claims.getStringClaim("role");
            return new AuthUser(UUID.fromString(sub), email, role);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw AppException.unauthorized();
        }
    }

    private ConfigurableJWTProcessor<SecurityContext> processor() throws Exception {
        if (processor != null) {
            return processor;
        }
        DefaultJWTProcessor<SecurityContext> p = new DefaultJWTProcessor<>();
        String secret = props.getSupabase().getJwtSecret();
        if (secret != null && !secret.isBlank()) {
            SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            p.setJWSKeySelector(new SingleKeyJWSKeySelector<>(JWSAlgorithm.HS256, key));
        } else {
            String jwks = props.getSupabase().getUrl().replaceAll("/$", "") + "/auth/v1/.well-known/jwks.json";
            JWKSource<SecurityContext> source = new RemoteJWKSet<>(URI.create(jwks).toURL());
            p.setJWSKeySelector(new JWSVerificationKeySelector<>(JWSAlgorithm.ES256, source));
        }
        this.processor = p;
        return processor;
    }
}
