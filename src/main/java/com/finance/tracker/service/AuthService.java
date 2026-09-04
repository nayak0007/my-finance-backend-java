package com.finance.tracker.service;

import com.finance.tracker.domain.Profile;
import com.finance.tracker.domain.Session;
import com.finance.tracker.exception.AppException;
import com.finance.tracker.repository.ProfileRepository;
import com.finance.tracker.repository.SessionRepository;
import com.finance.tracker.util.HashUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {

    private final SupabaseClient supabase;
    private final ProfileRepository profiles;
    private final SessionRepository sessions;

    public AuthService(SupabaseClient supabase, ProfileRepository profiles, SessionRepository sessions) {
        this.supabase = supabase;
        this.profiles = profiles;
        this.sessions = sessions;
    }

    @Transactional
    public Map<String, Object> signup(String name, String email, String password) {
        var created = supabase.adminCreateUser(email, password, name);
        Profile profile = upsertProfile(created.id(), name, email, "Free");
        try {
            var login = supabase.signIn(email, password);
            storeSession(created.id(), login.session().refreshToken(), null, null);
            Map<String, Object> session = sessionMap(login.session());
            return Map.of("profile", profileMap(profile), "session", session);
        } catch (AppException e) {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("profile", profileMap(profile));
            out.put("session", null);
            return out;
        }
    }

    @Transactional
    public Map<String, Object> login(String email, String password, String userAgent, String ip) {
        try {
            var result = supabase.signIn(email, password);
            String name = result.user().name() != null ? result.user().name() : email.split("@")[0];
            Profile profile = upsertProfile(result.user().id(), name, result.user().email() != null ? result.user().email() : email, null);
            storeSession(result.user().id(), result.session().refreshToken(), userAgent, ip);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("profile", profileMap(profile));
            out.put("access_token", result.session().accessToken());
            out.put("refresh_token", result.session().refreshToken());
            out.put("expires_in", result.session().expiresIn());
            out.put("token_type", result.session().tokenType());
            return out;
        } catch (AppException e) {
            throw AppException.unauthorized("Invalid email or password");
        }
    }

    @Transactional
    public Map<String, Object> refresh(String refreshToken, String userAgent, String ip) {
        var result = supabase.refresh(refreshToken);
        sessions.revokeByHash(HashUtil.sha256(refreshToken), Instant.now());
        storeSession(result.user().id(), result.session().refreshToken(), userAgent, ip);
        return sessionMap(result.session());
    }

    @Transactional
    public Map<String, Object> logout(String refreshToken, UUID userId) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            sessions.revokeByHash(HashUtil.sha256(refreshToken), Instant.now());
        } else if (userId != null) {
            sessions.revokeAllForUser(userId, Instant.now());
        }
        return Map.of("ok", true);
    }

    public Map<String, Object> me(UUID userId, String email) {
        Profile profile = profiles.findById(userId).orElse(null);
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", userId);
        user.put("email", email);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user", user);
        out.put("profile", profile == null ? null : profileMap(profile));
        return out;
    }

    public Map<String, Object> oauthUrl(String provider, String redirectTo) {
        if (!provider.equals("google") && !provider.equals("apple")) {
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
