package com.finance.tracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.tracker.config.AppProperties;
import com.finance.tracker.exception.AppException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NeonAuthClientTest {

    private final NeonAuthClient client = new NeonAuthClient(new AppProperties(), new ObjectMapper());

    @Test
    void mapsEmailExistsToConflict() {
        AppException ex = client.mapError(422, "{\"code\":\"USER_ALREADY_EXISTS\",\"message\":\"User already exists\"}");

        assertNotNull(ex);
        assertEquals(409, ex.getStatus());
        assertEquals("CONFLICT", ex.getCode());
        assertEquals("An account with this email address already exists", ex.getMessage());
    }

    @Test
    void mapsNumericCodeWithErrorCodeToConflict() {
        AppException ex = client.mapError(422, "{\"code\":422,\"error_code\":\"email_exists\",\"msg\":\"A user with this email address has already been registered\"}");

        assertNotNull(ex);
        assertEquals(409, ex.getStatus());
        assertEquals("CONFLICT", ex.getCode());
    }

    @Test
    void leavesOther422ErrorsUnmapped() {
        assertNull(client.mapError(422, "{\"code\":\"WEAK_PASSWORD\",\"message\":\"Password should be at least 8 characters\"}"));
    }

    @Test
    void mapsUnauthorizedStatuses() {
        AppException ex = client.mapError(401, "{\"code\":\"INVALID_EMAIL_OR_PASSWORD\",\"message\":\"Invalid email or password\"}");
        assertNotNull(ex);
        assertEquals(401, ex.getStatus());
        assertEquals("UNAUTHORIZED", ex.getCode());
    }

    @Test
    void leavesNonJsonBodyUnmapped() {
        assertNull(client.mapError(422, "<html>not json</html>"));
        assertNull(client.mapError(422, null));
    }

    @Test
    void oauthUrlIncludesProviderAndCallback() {
        AppProperties props = new AppProperties();
        props.getNeon().setAuthUrl("https://ep-cool.neonauth.us-east-2.aws.neon.tech/neondb/auth");
        NeonAuthClient auth = new NeonAuthClient(props, new ObjectMapper());
        String url = auth.oauthUrl("google", "http://localhost:8081");
        assertTrue(url.contains("/sign-in/social"));
        assertTrue(url.contains("provider=google"));
        assertTrue(url.contains("callbackURL="));
    }
}
