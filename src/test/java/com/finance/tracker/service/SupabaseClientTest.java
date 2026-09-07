package com.finance.tracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.tracker.config.AppProperties;
import com.finance.tracker.exception.AppException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SupabaseClientTest {

    private final SupabaseClient client = new SupabaseClient(new AppProperties(), new ObjectMapper());

    @Test
    void mapsEmailExistsToConflict() {
        AppException ex = client.mapError(422, "{\"code\":422,\"error_code\":\"email_exists\",\"msg\":\"A user with this email address has already been registered\"}");

        assertNotNull(ex);
        assertEquals(409, ex.getStatus());
        assertEquals("CONFLICT", ex.getCode());
        assertEquals("An account with this email address already exists", ex.getMessage());
    }

    @Test
    void leavesOther422ErrorsUnmapped() {
        assertNull(client.mapError(422, "{\"code\":422,\"error_code\":\"weak_password\",\"msg\":\"Password should be at least 6 characters\"}"));
    }

    @Test
    void leavesNon422StatusesUnmapped() {
        assertNull(client.mapError(400, "{\"code\":400,\"error_code\":\"invalid_credentials\",\"msg\":\"Invalid login credentials\"}"));
        assertNull(client.mapError(500, "{\"code\":500,\"msg\":\"boom\"}"));
    }

    @Test
    void leavesNonJsonBodyUnmapped() {
        assertNull(client.mapError(422, "<html>not json</html>"));
        assertNull(client.mapError(422, null));
    }
}