package com.finance.tracker.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.tracker.exception.AppException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

public final class HashUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HashUtil() {
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String encodeCursor(Instant date, UUID id) {
        try {
            String json = MAPPER.writeValueAsString(Map.of("date", date.toString(), "id", id.toString()));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AppException("BAD_REQUEST", "Could not encode cursor", 400);
        }
    }

    public static Cursor decodeCursor(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            var node = MAPPER.readTree(raw);
            return new Cursor(Instant.parse(node.get("date").asText()), UUID.fromString(node.get("id").asText()));
        } catch (Exception e) {
            throw AppException.badRequest("invalid cursor");
        }
    }

    public record Cursor(Instant date, UUID id) {
    }
}
