package com.finance.tracker.controller;

import com.finance.tracker.dto.AuthDtos;
import com.finance.tracker.security.AuthSupport;
import com.finance.tracker.security.AuthUser;
import com.finance.tracker.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> signup(@Valid @RequestBody AuthDtos.SignupRequest req) {
        return auth.signup(req.name(), req.email(), req.password());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody AuthDtos.LoginRequest req, HttpServletRequest http) {
        return auth.login(req.email(), req.password(), http.getHeader("User-Agent"), http.getRemoteAddr());
    }

    @PostMapping("/refresh")
    public Map<String, Object> refresh(@Valid @RequestBody AuthDtos.RefreshRequest req, HttpServletRequest http) {
        return auth.refresh(req.refreshToken(), http.getHeader("User-Agent"), http.getRemoteAddr());
    }

    @PostMapping("/forgot-password")
    public Map<String, Object> forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest req) {
        return auth.forgotPassword(req.email(), req.redirectUrl());
    }

    @PostMapping("/reset-password")
    public Map<String, Object> resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest req, HttpServletRequest http) {
        String header = http.getHeader("Authorization");
        String bearer = (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7))
                ? header.substring(7).trim()
                : null;
        String token = (req.token() != null && !req.token().isBlank()) ? req.token() : bearer;
        return auth.resetPassword(req.password(), token);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(@RequestBody(required = false) Map<String, String> body) {
        String token = body == null ? null : body.get("refresh_token");
        java.util.UUID userId = null;
        try {
            userId = AuthSupport.currentUserId();
        } catch (Exception ignored) {
        }
        return auth.logout(token, userId);
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        AuthUser user = AuthSupport.current();
        return auth.me(user.id(), user.email());
    }

    @GetMapping("/oauth/{provider}")
    public Map<String, Object> oauth(@PathVariable String provider, @RequestParam(value = "redirect_to", required = false) String redirectTo) {
        return auth.oauthUrl(provider, redirectTo);
    }
}
