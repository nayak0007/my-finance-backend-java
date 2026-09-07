package com.finance.tracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.tracker.exception.AppException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    public JwtAuthFilter(JwtService jwtService, ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if ("/health".equals(path) || path.startsWith("/error")) {
            return true;
        }
        if (path.startsWith("/auth/")) {
            return !path.equals("/auth/me");
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            log.debug("jwt missing bearer method={} path={}", request.getMethod(), request.getRequestURI());
            writeUnauthorized(response);
            return;
        }
        String token = header.substring(7).trim();
        if (token.isEmpty()) {
            log.debug("jwt empty bearer method={} path={}", request.getMethod(), request.getRequestURI());
            writeUnauthorized(response);
            return;
        }
        try {
            AuthUser user = jwtService.verify(token);
            MDC.put("uid", user.id().toString());
            log.debug("jwt ok user={} email={} role={} path={}", user.id(), user.email(), user.role(), request.getRequestURI());
            var auth = new UsernamePasswordAuthenticationToken(
                    user, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
            SecurityContextHolder.getContext().setAuthentication(auth);
            chain.doFilter(request, response);
        } catch (AppException ex) {
            log.warn("jwt rejected path={} reason={}", request.getRequestURI(), ex.getMessage());
            writeUnauthorized(response);
        }
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of(
                "error", Map.of("code", "UNAUTHORIZED", "message", "Missing or invalid authentication token")));
    }
}
