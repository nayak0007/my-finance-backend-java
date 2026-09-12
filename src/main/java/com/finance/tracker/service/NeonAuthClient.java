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
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class NeonAuthClient {

    private static final Logger log = LoggerFactory.getLogger(NeonAuthClient.class);
    private static final int JWT_TTL_SECONDS = 900;

    private final AppProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public NeonAuthClient(AppProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        warnIfPlaceholderConfig();
    }

    private void warnIfPlaceholderConfig() {
        String url = authBase();
        if (url.isBlank() || isPlaceholder(url)) {
            log.warn("NEON_AUTH_URL is still the placeholder ({}). Set NEON_AUTH_URL (e.g. in .env) or all Neon Auth calls will fail.", url);
        }
    }

    public record AuthUserInfo(UUID id, String email, String name) {}
    public record SessionTokens(String accessToken, String refreshToken, Integer expiresIn, String tokenType) {}
    public record AuthResult(AuthUserInfo user, SessionTokens session) {}

    public AuthUserInfo adminCreateUser(String email, String password, String name) {
        log.debug("neon auth signUp email={}", email);
        HttpResponse<String> res = post("/sign-up/email", Map.of(
                "email", email,
                "password", password,
                "name", name
        ));
        AuthUserInfo user = parseUser(unwrap(readTree(res.body())));
        if (user == null) {
            throw AppException.upstream("Failed to create user");
        }
        return user;
    }

    public AuthResult signIn(String email, String password) {
        log.debug("neon auth signIn email={}", email);
        HttpResponse<String> res = post("/sign-in/email", Map.of("email", email, "password", password));
        return parseAuth(readTree(res.body()), res);
    }

    public AuthResult refresh(String refreshToken) {
        log.debug("neon auth refresh token");
        if (refreshToken == null || refreshToken.isBlank()) {
            throw AppException.unauthorized("Invalid refresh token");
        }
        HttpResponse<String> tokenRes = get("/token", refreshToken);
        JsonNode tokenBody = readTree(tokenRes.body());
        String accessToken = firstText(tokenBody, "token", "access_token");
        if (accessToken == null) {
            accessToken = header(tokenRes, "set-auth-jwt");
        }
        HttpResponse<String> sessionRes = get("/get-session", refreshToken);
        JsonNode sessionBody = unwrap(readTree(sessionRes.body()));
        AuthUserInfo user = parseUser(sessionBody);
        String nextRefresh = sessionTokenFrom(sessionBody, sessionRes);
        if (nextRefresh == null) {
            nextRefresh = refreshToken;
        }
        if (accessToken == null) {
            accessToken = firstText(sessionBody.path("session"), "access_token", "token");
        }
        if (accessToken == null) {
            accessToken = header(sessionRes, "set-auth-jwt");
        }
        if (user == null || accessToken == null) {
            throw AppException.unauthorized("Invalid refresh token");
        }
        return new AuthResult(user, new SessionTokens(accessToken, nextRefresh, JWT_TTL_SECONDS, "bearer"));
    }

    public void signOut(String accessOrRefreshToken) {
        if (accessOrRefreshToken == null || accessOrRefreshToken.isBlank()) {
            return;
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(authBase() + "/sign-out"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessOrRefreshToken)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            originHeader().ifPresent(o -> builder.header("Origin", o));
            HttpRequest req = builder.POST(HttpRequest.BodyPublishers.ofString("{}")).build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
        }
    }

    /**
     * Asks Neon Auth to email a password-recovery link to the given address.
     * Unknown addresses are treated as success (anti-enumeration).
     */
    public void recover(String email, String redirectTo) {
        log.debug("neon auth requestPasswordReset email={} redirectTo={}", email, redirectTo);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        if (redirectTo != null && !redirectTo.isBlank()) {
            body.put("redirectTo", redirectTo);
        }
        post("/request-password-reset", body);
    }

    /**
     * Sets a new password using the one-time token from the recovery email.
     */
    public void updatePassword(String recoveryToken, String newPassword) {
        if (isPlaceholder(authBase())) {
            throw AppException.unauthorized("This reset link is invalid or has expired. Please request a new one.");
        }
        try {
            HttpResponse<String> res = postRaw("/reset-password", Map.of(
                    "newPassword", newPassword,
                    "token", recoveryToken
            ));
            log.debug("neon auth resetPassword status={}", res.statusCode());
            if (res.statusCode() >= 400) {
                log.warn("neon auth resetPassword failed status={} body={}", res.statusCode(), clip(res.body()));
                throw AppException.unauthorized("This reset link is invalid or has expired. Please request a new one.");
            }
        } catch (AppException e) {
            if ("UNAUTHORIZED".equals(e.getCode())) {
                throw e;
            }
            log.warn("neon auth resetPassword error: {}", e.getMessage());
            throw AppException.unauthorized("This reset link is invalid or has expired. Please request a new one.");
        } catch (Exception e) {
            log.warn("neon auth resetPassword error: {}", e.toString());
            throw AppException.unauthorized("This reset link is invalid or has expired. Please request a new one.");
        }
    }

    public String oauthUrl(String provider, String redirectTo) {
        StringBuilder url = new StringBuilder(authBase())
                .append("/sign-in/social?provider=").append(urlEncode(provider));
        if (redirectTo != null && !redirectTo.isBlank()) {
            url.append("&callbackURL=").append(urlEncode(redirectTo));
        }
        return url.toString();
    }

    private AuthResult parseAuth(JsonNode raw, HttpResponse<String> res) {
        JsonNode data = unwrap(raw);
        AuthUserInfo user = parseUser(data);
        if (user == null) {
            throw AppException.unauthorized("Invalid email or password");
        }
        String refreshToken = sessionTokenFrom(data, res);
        String accessToken = firstText(data.path("session"), "access_token", "token");
        if (accessToken == null) {
            accessToken = firstText(data, "access_token");
        }
        if (accessToken == null) {
            accessToken = header(res, "set-auth-jwt");
        }
        if (accessToken == null && refreshToken != null) {
            accessToken = fetchJwt(refreshToken);
        }
        if (accessToken == null) {
            throw AppException.unauthorized("Invalid email or password");
        }
        Integer expiresIn = expiresIn(data.path("session"));
        if (expiresIn == null) {
            expiresIn = JWT_TTL_SECONDS;
        }
        return new AuthResult(user, new SessionTokens(accessToken, refreshToken, expiresIn, "bearer"));
    }

    private AuthUserInfo parseUser(JsonNode data) {
        if (data == null || data.isMissingNode() || data.isNull()) {
            return null;
        }
        JsonNode userNode = data.path("user");
        if (userNode.isMissingNode() || userNode.path("id").isMissingNode()) {
            userNode = data.path("session").path("user");
        }
        if (userNode.isMissingNode() || userNode.path("id").isMissingNode()) {
            if (!data.path("id").isMissingNode() && !data.path("email").isMissingNode()) {
                userNode = data;
            }
        }
        if (userNode == null || userNode.isMissingNode() || userNode.path("id").isMissingNode()) {
            return null;
        }
        String id = userNode.path("id").asText(null);
        if (id == null || id.isBlank()) {
            return null;
        }
        String email = userNode.path("email").asText(null);
        String name = userNode.path("name").asText(null);
        if (name == null || name.isBlank()) {
            JsonNode meta = userNode.path("user_metadata");
            name = meta.path("name").asText(null);
        }
        return new AuthUserInfo(UUID.fromString(id), email, name);
    }

    private String sessionTokenFrom(JsonNode data, HttpResponse<String> res) {
        String token = firstText(data.path("session"), "token", "refresh_token");
        if (token == null) {
            token = firstText(data, "token", "refresh_token", "session_token");
        }
        if (token == null) {
            token = header(res, "set-auth-token");
        }
        if (token == null) {
            token = sessionCookie(res);
        }
        return token;
    }

    private String fetchJwt(String sessionToken) {
        try {
            HttpResponse<String> res = get("/token", sessionToken);
            JsonNode body = readTree(res.body());
            String jwt = firstText(body, "token", "access_token");
            if (jwt == null) {
                jwt = header(res, "set-auth-jwt");
            }
            return jwt;
        } catch (AppException e) {
            log.debug("neon auth fetchJwt failed: {}", e.getMessage());
            return null;
        }
    }

    private Integer expiresIn(JsonNode session) {
        if (session == null || session.isMissingNode()) {
            return null;
        }
        if (session.path("expires_in").isNumber()) {
            return session.path("expires_in").asInt();
        }
        if (session.path("expiresIn").isNumber()) {
            return session.path("expiresIn").asInt();
        }
        if (session.path("expires_at").isNumber()) {
            long exp = session.path("expires_at").asLong();
            long seconds = exp > 10_000_000_000L ? (exp / 1000L) - Instant.now().getEpochSecond() : exp - Instant.now().getEpochSecond();
            return (int) Math.max(seconds, 1);
        }
        String expiresAt = session.path("expiresAt").asText(null);
        if (expiresAt != null && !expiresAt.isBlank()) {
            try {
                long seconds = Instant.parse(expiresAt).getEpochSecond() - Instant.now().getEpochSecond();
                return (int) Math.max(seconds, 1);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private HttpResponse<String> post(String path, Map<String, Object> body) {
        HttpResponse<String> res = postRaw(path, body);
        if (res.statusCode() >= 400) {
            log.warn("neon auth POST {} status={} body={}", path, res.statusCode(), clip(res.body()));
            AppException mapped = mapError(res.statusCode(), res.body());
            throw mapped != null ? mapped : AppException.upstream("Auth request failed (" + res.statusCode() + ")");
        }
        log.debug("neon auth POST {} status={}", path, res.statusCode());
        return res;
    }

    private HttpResponse<String> postRaw(String path, Map<String, Object> body) {
        if (isPlaceholder(authBase())) {
            throw AppException.upstream("Neon Auth is not configured");
        }
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(authBase() + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE);
            originHeader().ifPresent(o -> builder.header("Origin", o));
            HttpRequest req = builder.POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            log.debug("neon auth POST {} origin={}", path, originHeader().orElse(""));
            return http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.warn("neon auth POST {} error: {}", path, e.toString());
            throw AppException.upstream("Auth request failed");
        }
    }

    private HttpResponse<String> get(String path, String bearer) {
        if (isPlaceholder(authBase())) {
            throw AppException.upstream("Neon Auth is not configured");
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(authBase() + path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + bearer)
                    .GET()
                    .build();
            log.debug("neon auth GET {}", path);
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 400) {
                log.warn("neon auth GET {} status={} body={}", path, res.statusCode(), clip(res.body()));
                AppException mapped = mapError(res.statusCode(), res.body());
                throw mapped != null ? mapped : AppException.upstream("Auth request failed (" + res.statusCode() + ")");
            }
            return res;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.warn("neon auth GET {} error: {}", path, e.toString());
            throw AppException.upstream("Auth request failed");
        }
    }

    /**
     * Translates known Neon Auth / Better Auth error responses into client-facing
     * AppExceptions. Returns null when the error should stay an upstream error.
     */
    AppException mapError(int statusCode, String body) {
        if (statusCode == 401 || statusCode == 403) {
            return AppException.unauthorized("Invalid email or password");
        }
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode node = mapper.readTree(body);
            String code = firstNonBlank(
                    textOrEmpty(node.path("error_code")),
                    textOrEmpty(node.path("code")),
                    textOrEmpty(node.path("error"))
            ).toUpperCase();
            String message = firstNonBlank(
                    textOrEmpty(node.path("message")),
                    textOrEmpty(node.path("msg"))
            ).toLowerCase();
            if (statusCode == 422 || statusCode == 409 || statusCode == 400) {
                if (code.contains("USER_ALREADY_EXISTS")
                        || code.contains("EMAIL_EXISTS")
                        || message.contains("already exists")
                        || message.contains("already registered")) {
                    return AppException.conflict("An account with this email address already exists");
                }
            }
            if (code.contains("INVALID_EMAIL_OR_PASSWORD")
                    || code.contains("INVALID_PASSWORD")
                    || code.contains("INVALID_CREDENTIALS")
                    || message.contains("invalid email or password")
                    || message.contains("invalid credentials")) {
                return AppException.unauthorized("Invalid email or password");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private JsonNode readTree(String body) {
        if (body == null || body.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private static JsonNode unwrap(JsonNode node) {
        if (node != null && node.has("data") && node.get("data").isObject()) {
            return node.get("data");
        }
        return node;
    }

    /**
     * Origin header value for server-to-server Neon Auth calls. Better Auth
     * rejects POSTs with MISSING_ORIGIN unless the body carries an absolute
     * callbackURL. Uses NEON_AUTH_ORIGIN when set, otherwise falls back to the
     * first configured CORS origin (the app's web origin).
     */
    private Optional<String> originHeader() {
        String explicit = props.getNeon().getAuthOrigin();
        if (explicit != null && !explicit.isBlank()) {
            return Optional.of(explicit.trim());
        }
        String cors = props.getCors().getOrigins();
        if (cors != null && !cors.isBlank()) {
            for (String candidate : cors.split(",")) {
                String origin = candidate.trim();
                if (!origin.isBlank() && !"*".equals(origin)) {
                    return Optional.of(origin);
                }
            }
        }
        return Optional.empty();
    }

    private String authBase() {
        String url = props.getNeon().getAuthUrl();
        if (url == null) {
            return "";
        }
        return url.replaceAll("/$", "");
    }

    static boolean isPlaceholder(String url) {
        if (url == null || url.isBlank()) {
            return true;
        }
        String u = url.toLowerCase();
        return u.contains("example.neon.tech")
                || u.contains("ep-example")
                || u.contains("your-project")
                || u.contains("ep-xxx");
    }

    private static String firstText(JsonNode node, String... keys) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.path(key);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText(null);
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static String header(HttpResponse<String> res, String name) {
        Optional<String> value = res.headers().firstValue(name);
        if (value.isPresent() && !value.get().isBlank()) {
            return value.get();
        }
        for (Map.Entry<String, List<String>> e : res.headers().map().entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name) && !e.getValue().isEmpty()) {
                String v = e.getValue().get(0);
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
        }
        return null;
    }

    private static String sessionCookie(HttpResponse<String> res) {
        List<String> cookies = res.headers().allValues("set-cookie");
        for (String cookie : cookies) {
            if (cookie == null) {
                continue;
            }
            String lower = cookie.toLowerCase();
            if (lower.contains("session_token=") || lower.contains("session-token=")) {
                int eq = cookie.indexOf('=');
                if (eq < 0) {
                    continue;
                }
                String rest = cookie.substring(eq + 1);
                int sc = rest.indexOf(';');
                String value = sc >= 0 ? rest.substring(0, sc) : rest;
                if (!value.isBlank()) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String textOrEmpty(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || node.isNumber()) {
            return "";
        }
        String text = node.asText("");
        return text == null ? "" : text;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String clip(String body) {
        if (body == null) {
            return null;
        }
        String trimmed = body.replaceAll("(?i)(\"(?:password|refresh_token|access_token|token|newPassword)\"\\s*:\\s*\")[^\"]*(\")", "$1***$2");
        return trimmed.length() <= 300 ? trimmed : trimmed.substring(0, 300) + "...";
    }
}
