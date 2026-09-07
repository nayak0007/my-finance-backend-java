package com.finance.tracker.logging;

import com.finance.tracker.security.AuthUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = header(request, "X-Request-Id");
        if (requestId == null) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }
        MDC.put("rid", requestId);
        response.setHeader("X-Request-Id", requestId);

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String path = query == null || query.isBlank() ? uri : uri + "?" + query;
        boolean health = "/health".equals(uri);
        long start = System.nanoTime();

        if (health) {
            log.debug("http in {} {}", method, path);
        } else {
            log.info("http in {} {} origin={} ip={} ua={}",
                    method, path, header(request, "Origin"), clientIp(request), abbreviate(header(request, "User-Agent"), 80));
        }
        try {
            chain.doFilter(request, response);
        } finally {
            long ms = (System.nanoTime() - start) / 1_000_000L;
            String user = currentUser();
            if (user != null) {
                MDC.put("uid", user);
            }
            if (health) {
                log.debug("http out {} {} status={} durationMs={}", method, uri, response.getStatus(), ms);
            } else {
                log.info("http out {} {} status={} durationMs={} user={}", method, uri, response.getStatus(), ms, user);
            }
            MDC.clear();
        }
    }

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user.id().toString();
        }
        return MDC.get("uid");
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = header(request, "X-Forwarded-For");
        if (forwarded != null) {
            int comma = forwarded.indexOf(',');
            return comma < 0 ? forwarded : forwarded.substring(0, comma).trim();
        }
        return request.getRemoteAddr();
    }

    private static String header(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String abbreviate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
