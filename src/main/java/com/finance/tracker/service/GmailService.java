package com.finance.tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.tracker.config.AppProperties;
import com.finance.tracker.exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Thin client for Google OAuth2 and the Gmail API. All credentials live
 * server-side (app.gmail.*); the mobile app only opens the auth URL in a
 * browser and lets Google redirect back to the backend callback.
 */
@Service
public class GmailService {

    private static final Logger log = LoggerFactory.getLogger(GmailService.class);
    private static final String AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String GMAIL_API = "https://gmail.googleapis.com/gmail/v1/users/me";

    private final AppProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public GmailService(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
    }

    public record TokenResponse(String accessToken, String refreshToken, Integer expiresIn, String idToken) {}
    public record GmailMessage(String id, String subject, String from, String body, long epochMillis) {}

    /** Google OAuth consent URL with the given one-time state. */
    public String buildAuthUrl(String state) {
        AppProperties.Gmail g = props.getGmail();
        requireConfigured();
        return AUTH_ENDPOINT
                + "?client_id=" + enc(g.getClientId())
                + "&redirect_uri=" + enc(g.getRedirectUri())
                + "&response_type=code"
                + "&scope=" + enc(g.getScopes())
                + "&access_type=offline"
                + "&prompt=consent"
                + "&state=" + enc(state);
    }

    /** Exchanges the authorization code for access + refresh tokens. */
    public TokenResponse exchangeCode(String code) {
        requireConfigured();
        AppProperties.Gmail g = props.getGmail();
        String body = "code=" + enc(code)
                + "&client_id=" + enc(g.getClientId())
                + "&client_secret=" + enc(g.getClientSecret())
                + "&redirect_uri=" + enc(g.getRedirectUri())
                + "&grant_type=authorization_code";
        return parseToken(postForm(TOKEN_ENDPOINT, body));
    }

    /** Uses a refresh token to mint a fresh access token. */
    public TokenResponse refreshAccessToken(String refreshToken) {
        requireConfigured();
        AppProperties.Gmail g = props.getGmail();
        String body = "refresh_token=" + enc(refreshToken)
                + "&client_id=" + enc(g.getClientId())
                + "&client_secret=" + enc(g.getClientSecret())
                + "&grant_type=refresh_token";
        return parseToken(postForm(TOKEN_ENDPOINT, body));
    }

    /** Revokes the OAuth token so the user's consent is withdrawn. */
    public void revokeToken(String token) {
        if (token == null || token.isBlank()) return;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://oauth2.googleapis.com/revoke?token=" + enc(token)))
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            log.debug("gmail revoke failed: {}", e.getMessage());
        }
    }

    /** Lists message ids matching the query, most recent first. */
    public List<String> listMessages(String accessToken, String query, int maxResults) {
        try {
            StringBuilder url = new StringBuilder(GMAIL_API).append("/messages?maxResults=").append(maxResults);
            if (query != null && !query.isBlank()) {
                url.append("&q=").append(enc(query));
            }
            HttpResponse<String> res = get(url.toString(), accessToken);
            if (res.statusCode() >= 400) {
                throw upstream("Gmail API list failed (" + res.statusCode() + ")");
            }
            JsonNode root = mapper.readTree(res.body());
            List<String> ids = new ArrayList<>();
            for (JsonNode node : root.path("messages")) {
                String id = node.path("id").asText(null);
                if (id != null) ids.add(id);
            }
            return ids;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw upstream("Gmail API list failed");
        }
    }

    /** Fetches one message and extracts subject / sender / plain-text body. */
    public GmailMessage getMessage(String accessToken, String id) {
        try {
            HttpResponse<String> res = get(GMAIL_API + "/messages/" + enc(id) + "?format=full", accessToken);
            if (res.statusCode() >= 400) {
                throw upstream("Gmail API message failed (" + res.statusCode() + ")");
            }
            JsonNode root = mapper.readTree(res.body());
            String subject = "";
            String from = "";
            for (JsonNode header : root.path("payload").path("headers")) {
                String name = header.path("name").asText("");
                if ("Subject".equalsIgnoreCase(name)) subject = header.path("value").asText("");
                else if ("From".equalsIgnoreCase(name)) from = header.path("value").asText("");
            }
            String body = extractBody(root.path("payload"), new StringBuilder());
            long epochMillis = root.path("internalDate").asLong(0);
            return new GmailMessage(id, subject, from, body, epochMillis);
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw upstream("Gmail API message failed");
        }
    }

    private String extractBody(JsonNode part, StringBuilder out) {
        String mime = part.path("mimeType").asText("");
        if ("text/plain".equalsIgnoreCase(mime)) {
            out.append(decode(part.path("body").path("data").asText(""))).append('\n');
        } else if ("text/html".equalsIgnoreCase(mime) && out.length() == 0) {
            // Only use HTML when there is no plain part.
            out.append(decode(part.path("body").path("data").asText(""))).append('\n');
        }
        for (JsonNode child : part.path("parts")) {
            extractBody(child, out);
        }
        return out.toString().trim();
    }

    private static String decode(String base64Url) {
        if (base64Url == null || base64Url.isBlank()) return "";
        try {
            return new String(Base64.getUrlDecoder().decode(base64Url), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            try {
                return new String(Base64.getDecoder().decode(base64Url), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ex) {
                return "";
            }
        }
    }

    private TokenResponse parseToken(String responseBody) {
        try {
            JsonNode node = mapper.readTree(responseBody);
            if (node.has("error")) {
                throw AppException.upstream("Google rejected the OAuth request: " + node.path("error").asText());
            }
            return new TokenResponse(
                    node.path("access_token").asText(null),
                    node.path("refresh_token").asText(null),
                    node.path("expires_in").isNumber() ? node.path("expires_in").asInt() : null,
                    node.path("id_token").asText(null)
            );
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            throw upstream("Could not parse Google token response");
        }
    }

    /** Extracts the account email from the Google id_token JWT payload. */
    public static String emailFromIdToken(String idToken) {
        if (idToken == null || idToken.isBlank()) return null;
        try {
            String payload = idToken.split("\\.")[1];
            byte[] decoded = Base64.getUrlDecoder().decode(payload);
            JsonNode node = new ObjectMapper().readTree(decoded);
            String email = node.path("email").asText(null);
            return (email == null || email.isBlank()) ? null : email;
        } catch (Exception e) {
            return null;
        }
    }

    private void requireConfigured() {
        AppProperties.Gmail g = props.getGmail();
        if (g.getClientId() == null || g.getClientId().isBlank()
                || g.getClientSecret() == null || g.getClientSecret().isBlank()
                || g.getRedirectUri() == null || g.getRedirectUri().isBlank()) {
            throw AppException.upstream("Gmail sync is not configured on the server (GOOGLE_CLIENT_ID / GOOGLE_CLIENT_SECRET / GOOGLE_REDIRECT_URI)");
        }
    }

    private String postForm(String url, String formBody) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                log.warn("gmail token endpoint status={} body={}", res.statusCode(), clip(res.body()));
                throw AppException.upstream("Google OAuth exchange failed (" + res.statusCode() + ")");
            }
            return res.body();
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.warn("gmail token endpoint error: {}", e.toString());
            throw upstream("Google OAuth exchange failed");
        }
    }

    private HttpResponse<String> get(String url, String accessToken) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            return http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.warn("gmail api error: {}", e.toString());
            throw upstream("Gmail API request failed");
        }
    }

    private AppException upstream(String message) {
        return AppException.upstream(message);
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String clip(String body) {
        if (body == null) return null;
        String trimmed = body.replaceAll("(?i)(\"(?:access_token|refresh_token)\"\\s*:\\s*\")[^\"]*(\")", "$1***$2");
        return trimmed.length() <= 200 ? trimmed : trimmed.substring(0, 200) + "...";
    }
}