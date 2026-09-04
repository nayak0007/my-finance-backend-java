package com.finance.tracker.security;

import com.finance.tracker.exception.AppException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class AuthSupport {
    private AuthSupport() {
    }

    public static AuthUser current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthUser user)) {
            throw AppException.unauthorized();
        }
        return user;
    }

    public static UUID currentUserId() {
        return current().id();
    }
}
