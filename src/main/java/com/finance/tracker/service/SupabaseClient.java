package com.finance.tracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.tracker.config.AppProperties;
import com.finance.tracker.exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Component
public class SupabaseClient {

    private static final Logger log = LoggerFactory.getLogger(SupabaseClient.class);

    private final AppProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public SupabaseClient(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        warnIfPlaceholderConfig();
    }

    private void warnIfPlaceholderConfig() {
        String url = props.getSupabase().getUrl();
        if (url.contains("example.supabase.co") || url.contains("your-project.supabase.co")) {
            log.warn("SUPABASE_URL is still the placeholder ({}). Set SUPABASE_URL, SUPABASE_ANON_KEY and "
                    + "SUPABASE_SERVICE_ROLE_KEY (e.g. in .env) or all Supabase auth calls will fail.", url);
        }
    }

    public record AuthUserInfo(UUID id, String email, String name) {}
    public record SessionTokens(String accessToken, String refreshToken, Integer expiresIn, String tokenType) {}
    public record AuthResult(AuthUserInfo user, SessionTokens session) {}

    public AuthUserInfo adminCreateUser(String email, String password, String name) {
        log.debug("supabase adminCreateUser email={}", email);
        JsonNode data = post(
                props.getSupabase().getUrl().replaceAll("/$", "") + "/auth/v1/admin/users",
                props.getSupabase().getServiceRoleKey(),
                Map.of(
                        "email", email,
                        "password", password,
                        "email_confirm", true,
                        "user_metadata", Map.of("name", name)
                )
        );
        JsonNode user = data.path("id").isMissingNode() ? data.path("user") : data;
        if (user.path("id").isMissingNode()) {
            throw AppException.upstream("Failed to create user");
        }
        return new AuthUserInfo(UUID.fromString(user.path("id").asText()), email, name);
    }

    public AuthResult signIn(String email, String password) {
        log.debug("supabase signIn email={}", email);
        JsonNode data = post(
                props.getSupabase().getUrl().replaceAll("/$", "") + "/auth/v1/token?grant_type=password",
                props.getSupabase().getAnonKey(),
                Map.of("email", email, "password", password)
        );
        return parseAuth(data);
    }

    public AuthResult refresh(String refreshToken) {
        log.debug("supabase refresh token");
        JsonNode data = post(
                props.getSupabase().getUrl().replaceAll("/$", "") + "/auth/v1/token?grant_type=refresh_token",
                props.getSupabase().getAnonKey(),
                Map.of("refresh_token", refreshToken)
        );
        return parseAuth(data);
    }

    public void signOut(String accessToken) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(props.getSupabase().getUrl().replaceAll("/$", "") + "/auth/v1/logout"))
                    .timeout(Duration.ofSeconds(15))
                    .header("apikey", props.getSupabase().getAnonKey())
                    .header("Authorization", "Bearer " + accessToken)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }

    /**
     * Asks Supabase to email a password-recovery link to the given address.
     * Supabase responds 200 even for unknown addresses (anti-enumeration), and
     * the recovery email is only sent when the account exists.
     */
    public void recover(String email, String redirectTo) {
        log.debug("supabase recover email={} redirectTo={}", email, redirectTo);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("email", email);
        if (redirectTo != null && !redirectTo.isBlank()) {
            body.put("options", Map.of("redirect_to", redirectTo));
        }
        post(
                props.getSupabase().getUrl().replaceAll("/$", "") + "/auth/v1/recover",
                props.getSupabase().getAnonKey(),
                body
        );
    }

    /**
     * Sets a new password using the one-time access token from the recovery
     * email (the session issued with type=recovery). The token is sent to
     * Supabase itself so it can reject expired or already-used links.
     */
    public void updatePassword(String recoveryAccessToken, String newPassword) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(props.getSupabase().getUrl().replaceAll("/$", "") + "/auth/v1/user"))
                    .timeout(Duration.ofSeconds(20))
                    .header("apikey", props.getSupabase().getAnonKey())
                    .header("Authorization", "Bearer " + recoveryAccessToken)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .PUT(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("password", newPassword))))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            log.debug("supabase updatePassword status={}", res.statusCode());
            if (res.statusCode() >= 400) {
                log.warn("supabase updatePassword failed status={} body={}", res.statusCode(), clip(res.body()));
                if (res.statusCode() == 401 || res.statusCode() == 403) {
                    throw AppException.unauthorized("This reset link is invalid or has expired. Please request a new one.");
                }
                throw AppException.upstream("Password update failed (" + res.statusCode() + ")");
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.warn("supabase updatePassword error: {}", e);
            throw AppException.upstream("Password update failed");
        }
    }

    public String oauthUrl(String provider, String redirectTo) {
        StringBuilder url = new StringBuilder(props.getSupabase().getUrl().replaceAll("/$", ""))
                .append("/auth/v1/authorize?provider=").append(provider);
        if (redirectTo != null && !redirectTo.isBlank()) {
            url.append("&redirect_to=").append(java.net.URLEncoder.encode(redirectTo, java.nio.charset.StandardCharsets.UTF_8));
        }
        return url.toString();
    }

    private AuthResult parseAuth(JsonNode data) {
        JsonNode userNode = data.path("user");
        if (userNode.isMissingNode() || userNode.path("id").isMissingNode()) {
            throw AppException.unauthorized("Invalid email or password");
        }
        String name = userNode.path("user_metadata").path("name").asText(null);
        AuthUserInfo user = new AuthUserInfo(
                UUID.fromString(userNode.path("id").asText()),
                userNode.path("email").asText(null),
                name
        );
        SessionTokens session = new SessionTokens(
                data.path("access_token").asText(null),
                data.path("refresh_token").asText(null),
                data.path("expires_in").isNumber() ? data.path("expires_in").asInt() : null,
                data.path("token_type").asText("bearer")
        );
        if (session.accessToken() == null) {
            throw AppException.unauthorized("Invalid email or password");
        }
        return new AuthResult(user, session);
    }

    private JsonNode post(String url, String apiKey, Map<String, Object> body) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("apikey", apiKey)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .build();
            log.debug("supabase POST {}", pathOf(url));
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                log.warn("supabase POST {} status={} body={}", pathOf(url), res.statusCode(), clip(res.body()));
                AppException mapped = mapError(res.statusCode(), res.body());
                throw mapped != null ? mapped : AppException.upstream("Auth request failed (" + res.statusCode() + ")");
            }
            log.debug("supabase POST {} status={}", pathOf(url), res.statusCode());
            return mapper.readTree(res.body());
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.warn("supabase POST {} error: {}", url, e);
            throw AppException.upstream("Auth request failed");
        }
    }

    /**
     * Translates known Supabase error responses into proper client-facing
     * AppExceptions. Returns null when the error is not something the client
     * should see as a specific status and should stay an upstream error.
     */
    AppException mapError(int statusCode, String body) {
        if (statusCode != 422 || body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(body);
            if ("email_exists".equals(node.path("error_code").asText(""))) {
                return AppException.conflict("An account with this email address already exists");
            }
        } catch (Exception ignored) {
            // body was not JSON; fall through to the generic upstream error
        }
        return null;
    }

    private static String pathOf(String url) {
        try {
            URI uri = URI.create(url);
            String path = uri.getPath();
            String query = uri.getQuery();
            return query == null ? path : path + "?" + query;
        } catch (Exception e) {
            return url;
        }
    }

    private static String clip(String body) {
        if (body == null) {
            return null;
        }
        String trimmed = body.replaceAll("(?i)(\"(?:password|refresh_token|access_token|token)\"\\s*:\\s*\")[^\"]*(\")", "$1***$2");
        return trimmed.length() <= 300 ? trimmed : trimmed.substring(0, 300) + "...";
    }
}
