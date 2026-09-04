package com.finance.tracker;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

public final class JwtTokens {
    public static final String SECRET = "test-supabase-jwt-secret-which-is-long-enough";
    public static final UUID USER = UUID.fromString("00000000-0000-4000-8000-000000000001");
    public static final UUID OTHER = UUID.fromString("00000000-0000-4000-8000-000000000002");

    private JwtTokens() {}

    public static String token(UUID userId, String email) {
        return token(userId, email, Instant.now().plusSeconds(3600));
    }

    public static String token(UUID userId, String email, Instant exp) {
        try {
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .claim("email", email)
                    .claim("role", "authenticated")
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(exp))
                    .issuer("https://example.supabase.co/auth/v1")
                    .audience("authenticated")
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(SECRET.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
