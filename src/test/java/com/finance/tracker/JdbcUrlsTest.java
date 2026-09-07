package com.finance.tracker;

import com.finance.tracker.config.JdbcUrls;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JdbcUrlsTest {

    @Test
    void convertsRenderInternalUrlAndPrefersEmbeddedCredentials() {
        JdbcUrls.Parsed parsed = JdbcUrls.parse(
                "postgresql://finance_user:s3cret@dpg-abc123-a:5432/finance",
                "finance",
                "finance");
        assertEquals("jdbc:postgresql://dpg-abc123-a:5432/finance?stringtype=unspecified", parsed.jdbcUrl());
        assertEquals("finance_user", parsed.user());
        assertEquals("s3cret", parsed.password());
    }

    @Test
    void convertsExternalRenderUrlWithEncodedPassword() {
        JdbcUrls.Parsed parsed = JdbcUrls.parse(
                "postgres://app:p%40ss%2Fword@dpg-abc123-a.oregon-postgres.render.com/finance",
                null,
                null);
        assertEquals("jdbc:postgresql://dpg-abc123-a.oregon-postgres.render.com:5432/finance?stringtype=unspecified&sslmode=require", parsed.jdbcUrl());
        assertEquals("app", parsed.user());
        assertEquals("p@ss/word", parsed.password());
    }

    @Test
    void leavesLocalhostWithoutForcedSsl() {
        JdbcUrls.Parsed parsed = JdbcUrls.parse(
                "jdbc:postgresql://localhost:5432/finance",
                "finance",
                "finance");
        assertEquals("jdbc:postgresql://localhost:5432/finance?stringtype=unspecified", parsed.jdbcUrl());
        assertEquals("finance", parsed.user());
        assertEquals("finance", parsed.password());
    }

    @Test
    void doesNotDuplicateExistingQueryParams() {
        JdbcUrls.Parsed parsed = JdbcUrls.parse(
                "postgresql://u:p@db.example.com:5432/finance?sslmode=prefer&stringtype=unspecified",
                "ignored",
                "ignored");
        assertEquals("jdbc:postgresql://db.example.com:5432/finance?sslmode=prefer&stringtype=unspecified", parsed.jdbcUrl());
        assertEquals("u", parsed.user());
        assertEquals("p", parsed.password());
    }
}
