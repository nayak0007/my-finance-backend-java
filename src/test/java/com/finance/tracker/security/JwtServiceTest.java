package com.finance.tracker.security;

import com.finance.tracker.config.AppProperties;
import com.finance.tracker.exception.AppException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void verifiesHs256TokenSignedWithConfiguredSecret() throws Exception {
        JwtService service = new JwtService(propsWithSecret(SECRET));
        UUID id = UUID.randomUUID();

        AuthUser user = service.verify(hs256(id, "user@example.com", SECRET));

        assertEquals(id, user.id());
        assertEquals("user@example.com", user.email());
    }

    @Test
    void rejectsHs256TokenSignedWithAnotherSecret() throws Exception {
        JwtService service = new JwtService(propsWithSecret(SECRET));

        AppException ex = assertThrows(AppException.class,
                () -> service.verify(hs256(UUID.randomUUID(), "user@example.com", "ffffffffffffffffffffffffffffffff")));

        assertEquals(401, ex.getStatus());
        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    /**
     * Regression: Neon Auth signs with EdDSA. A leftover NEON_JWT_SECRET used to make the
     * single HS256 verifier reject those tokens ("Another algorithm expected"), so every
     * authenticated request 401'd right after a successful login.
     */
    @Test
    void routesEdDsaTokensToJwksInsteadOfTheHs256Secret() {
        JwtService service = new JwtService(propsWithSecret(SECRET));

        AppException ex = assertThrows(AppException.class,
                () -> service.verify(handCrafted("EdDSA", UUID.randomUUID())));

        // Reaching the JWKS branch (instead of failing signature verification against the
        // HMAC secret) is the point: it reports the missing provider config, not a bad token.
        assertEquals("UPSTREAM_ERROR", ex.getCode());
        assertEquals(502, ex.getStatus());
        assertTrue(ex.getMessage().contains("not configured"), ex.getMessage());
    }

    @Test
    void rejectsAlgorithmsOutsideTheAllowList() {
        JwtService service = new JwtService(propsWithSecret(SECRET));

        AppException ex = assertThrows(AppException.class,
                () -> service.verify(handCrafted("HS384", UUID.randomUUID())));

        assertEquals("UNAUTHORIZED", ex.getCode());
        assertTrue(ex.getMessage().contains("algorithm"), ex.getMessage());
    }

    @Test
    void rejectsTokenWithoutSubject() {
        JwtService service = new JwtService(propsWithSecret(SECRET));

        AppException ex = assertThrows(AppException.class,
                () -> service.verify(handCrafted("HS256", null)));

        assertEquals(401, ex.getStatus());
    }

    @Test
    void rejectsMalformedToken() {
        JwtService service = new JwtService(propsWithSecret(SECRET));

        AppException ex = assertThrows(AppException.class, () -> service.verify("not-a-jwt"));

        assertEquals(401, ex.getStatus());
    }

    private static AppProperties propsWithSecret(String secret) {
        AppProperties props = new AppProperties();
        props.getNeon().setJwtSecret(secret);
        return props;
    }

    private static String hs256(UUID sub, String email, String secret) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(sub.toString())
                .claim("email", email)
                .expirationTime(new Date(System.currentTimeMillis() + 600_000))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    /** Builds a token with an arbitrary header alg, so routing can be tested without keys. */
    private static String handCrafted(String alg, UUID sub) {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String header = b64.encodeToString(("{\"alg\":\"" + alg + "\"}").getBytes(StandardCharsets.UTF_8));
        String payload = "{}";
        if (sub != null) {
            payload = "{\"sub\":\"" + sub + "\"}";
        }
        String body = b64.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return header + "." + body + "." + b64.encodeToString(new byte[]{1, 2, 3});
    }
}
