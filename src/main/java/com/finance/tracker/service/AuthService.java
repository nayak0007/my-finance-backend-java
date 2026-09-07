package com.finance.tracker.service;

import com.finance.tracker.config.AppProperties;
import com.finance.tracker.domain.Profile;
import com.finance.tracker.domain.Session;
import com.finance.tracker.exception.AppException;
import com.finance.tracker.repository.ProfileRepository;
import com.finance.tracker.repository.SessionRepository;
import com.finance.tracker.security.JwtService;
import com.finance.tracker.security.AuthUser;
import com.finance.tracker.util.HashUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final SupabaseClient supabase;
    private final ProfileRepository profiles;
    private final SessionRepository sessions;
    private final JwtService jwtService;
    private final AppProperties props;

    public AuthService(SupabaseClient supabase, ProfileRepository profiles, SessionRepository sessions,
                       JwtService jwtService, AppProperties props) {
        this.supabase = supabase;
        this.profiles = profiles;
        this.sessions = sessions;
        this.jwtService = jwtService;
        this.props = props;
    }

    @Transactional
    public Map<String, Object> signup(String name, String email, String password) {
        log.info("auth signup start email={}", email);
        var created = supabase.adminCreateUser(email, password, name);
        log.info("auth signup user created id={}", created.id());
        Profile profile = upsertProfile(created.id(), name, email, "Free");
        try {
            var login = supabase.signIn(email, password);
            storeSession(created.id(), login.session().refreshToken(), null, null);
            Map<String, Object> session = sessionMap(login.session());
            log.info("auth signup ok user={} hasSession=true", created.id());
            return Map.of("profile", profileMap(profile), "session", session);
        } catch (AppException e) {
            log.warn("auth signup created but auto-login failed user={} reason={}", created.id(), e.getMessage());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("profile", profileMap(profile));
            out.put("session", null);
            return out;
        }
    }

    @Transactional
    public Map<String, Object> login(String email, String password, String userAgent, String ip) {
        log.info("auth login start email={} ip={}", email, ip);
        try {
            var result = supabase.signIn(email, password);
            String name = result.user().name() != null ? result.user().name() : email.split("@")[0];
            Profile profile = upsertProfile(result.user().id(), name, result.user().email() != null ? result.user().email() : email, null);
            storeSession(result.user().id(), result.session().refreshToken(), userAgent, ip);
            log.info("auth login ok user={} plan={}", result.user().id(), profile.getPlan());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("profile", profileMap(profile));
            out.put("access_token", result.session().accessToken());
            out.put("refresh_token", result.session().refreshToken());
            out.put("expires_in", result.session().expiresIn());
            out.put("token_type", result.session().tokenType());
            return out;
        } catch (AppException e) {
            log.warn("auth login failed email={} reason={}", email, e.getMessage());
            throw AppException.unauthorized("Invalid email or password");
        }
    }

    @Transactional
    public Map<String, Object> refresh(String refreshToken, String userAgent, String ip) {
        log.info("auth refresh start ip={}", ip);
        var result = supabase.refresh(refreshToken);
        sessions.revokeByHash(HashUtil.sha256(refreshToken), Instant.now());
        storeSession(result.user().id(), result.session().refreshToken(), userAgent, ip);
        log.info("auth refresh ok user={}", result.user().id());
        return sessionMap(result.session());
    }

    @Transactional
    public Map<String, Object> logout(String refreshToken, UUID userId) {
        log.info("auth logout start user={} hasRefreshToken={}", userId, refreshToken != null && !refreshToken.isBlank());
        if (refreshToken != null && !refreshToken.isBlank()) {
            sessions.revokeByHash(HashUtil.sha256(refreshToken), Instant.now());
        } else if (userId != null) {
            sessions.revokeAllForUser(userId, Instant.now());
        }
        log.info("auth logout ok user={}", userId);
        return Map.of("ok", true);
    }

    public Map<String, Object> me(UUID userId, String email) {
        log.debug("auth me user={} email={}", userId, email);
        Profile profile = profiles.findById(userId).orElse(null);
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", userId);
        user.put("email", email);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user", user);
        out.put("profile", profile == null ? null : profileMap(profile));
        return out;
    }

    /**
     * Sends a password-recovery email (via Supabase) for the given address.
     * Always answers ok — regardless of whether the account exists or the email
     * provider is slow/failing — so the endpoint cannot be used to probe which
     * emails are registered.
     */
    @Transactional
    public Map<String, Object> forgotPassword(String email, String redirectUrl) {
        String target = (redirectUrl == null || redirectUrl.isBlank())
                ? props.getAuth().getRedirectUrl()
                : redirectUrl;
        log.info("auth forgot-password start email={} redirect={}", email, blankToNull(target));
        try {
            supabase.recover(email, target);
            log.info("auth forgot-password supabase accepted email={}", email);
        } catch (AppException e) {
            log.warn("auth forgot-password supabase failed email={} reason={}", email, e.getMessage());
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        return out;
    }

    /**
     * Completes a password reset using the one-time access token carried by the
     * recovery email link (Authorization: Bearer). Verifies the token locally to
     * identify the user, then lets Supabase apply the new password — Supabase
     * rejects links that are expired or were already consumed.
     */
    @Transactional
    public Map<String, Object> resetPassword(String password, String accessToken) {
        log.info("auth reset-password start hasToken={}", accessToken != null && !accessToken.isBlank());
        if (accessToken == null || accessToken.isBlank()) {
            log.warn("auth reset-password missing recovery token");
            throw AppException.unauthorized("This reset link is invalid or has expired. Please request a new one.");
        }
        AuthUser user;
        try {
            user = jwtService.verify(accessToken);
        } catch (AppException e) {
            log.warn("auth reset-password token verify failed: {}", e.getMessage());
            throw AppException.unauthorized("This reset link is invalid or has expired. Please request a new one.");
        }
        log.info("auth reset-password verified user={}", user.id());
        supabase.updatePassword(accessToken, password);
        sessions.revokeAllForUser(user.id(), Instant.now());
        log.info("auth reset-password ok user={} sessionsRevoked=all", user.id());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("message", "Password updated. You can now sign in with your new password.");
        return out;
    }

    public Map<String, Object> oauthUrl(String provider, String redirectTo) {
        log.info("auth oauth start provider={} redirectTo={}", provider, blankToNull(redirectTo));
        if (!provider.equals("google") && !provider.equals("apple")) {
            log.warn("auth oauth invalid provider={}", provider);
            throw AppException.validation("Invalid provider", null);
        }
        return Map.of("url", supabase.oauthUrl(provider, redirectTo));
    }

    private Profile upsertProfile(UUID id, String name, String email, String plan) {
        Profile profile = profiles.findById(id).orElseGet(Profile::new);
        profile.setId(id);
        profile.setName(name);
        profile.setEmail(email);
        if (profile.getPlan() == null || plan != null) {
            profile.setPlan(plan == null ? "Free" : plan);
        }
        if (profile.getMemberSince() == null) {
            profile.setMemberSince(Instant.now());
        }
        if (profile.getCreatedAt() == null) {
            profile.setCreatedAt(Instant.now());
        }
        return profiles.save(profile);
    }

    private void storeSession(UUID userId, String refreshToken, String userAgent, String ip) {
        if (refreshToken == null) {
            return;
        }
        String hash = HashUtil.sha256(refreshToken);
        Session existing = sessions.findByRefreshTokenHashAndRevokedAtIsNull(hash).orElse(null);
        Session session = existing == null ? new Session() : existing;
        session.setUserId(userId);
        session.setRefreshTokenHash(hash);
        session.setUserAgent(userAgent);
        session.setIp(ip);
        session.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        session.setRevokedAt(null);
        sessions.save(session);
    }

    private Map<String, Object> sessionMap(SupabaseClient.SessionTokens s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("access_token", s.accessToken());
        m.put("refresh_token", s.refreshToken());
        m.put("expires_in", s.expiresIn());
        m.put("token_type", s.tokenType());
        return m;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private Map<String, Object> profileMap(Profile p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("name", p.getName());
        m.put("email", p.getEmail());
        m.put("plan", p.getPlan());
        m.put("member_since", p.getMemberSince());
        m.put("created_at", p.getCreatedAt());
        return m;
    }
}
