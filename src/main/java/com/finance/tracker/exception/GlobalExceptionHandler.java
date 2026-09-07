package com.finance.tracker.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, Object>> handleApp(AppException ex, HttpServletRequest req) {
        log.warn("app error path={} status={} code={} message={}", req.getRequestURI(), ex.getStatus(), ex.getCode(), ex.getMessage());
        return envelope(ex.getStatus(), ex.getCode(), ex.getMessage(), ex.getDetails());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<Map<String, String>> details = ex.getBindingResult().getFieldErrors().stream()
                .map(this::fieldIssue)
                .toList();
        log.warn("validation error path={} fields={}", req.getRequestURI(), details.size());
        return envelope(400, "VALIDATION_ERROR", "Invalid request", details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraint(ConstraintViolationException ex, HttpServletRequest req) {
        List<Map<String, String>> details = ex.getConstraintViolations().stream()
                .map(v -> Map.of("path", v.getPropertyPath().toString(), "message", v.getMessage()))
                .toList();
        log.warn("constraint error path={} fields={}", req.getRequestURI(), details.size());
        return envelope(400, "VALIDATION_ERROR", "Invalid request", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        log.warn("unreadable body path={}", req.getRequestURI());
        return envelope(400, "VALIDATION_ERROR", "Invalid JSON body", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleUpload(MaxUploadSizeExceededException ex, HttpServletRequest req) {
        log.warn("upload too large path={}", req.getRequestURI());
        return envelope(400, "BAD_REQUEST", "File too large", null);
    }

    @ExceptionHandler({AuthenticationException.class})
    public ResponseEntity<Map<String, Object>> handleAuth(AuthenticationException ex, HttpServletRequest req) {
        log.warn("auth error path={} reason={}", req.getRequestURI(), ex.getMessage());
        return envelope(401, "UNAUTHORIZED", "Missing or invalid authentication token", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleDenied(AccessDeniedException ex, HttpServletRequest req) {
        log.warn("forbidden path={}", req.getRequestURI());
        return envelope(403, "FORBIDDEN", "You do not have access to this resource", null);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NoHandlerFoundException ex) {
        log.debug("route not found {}", ex.getRequestURL());
        return envelope(404, "NOT_FOUND", "Route not found", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception ex, HttpServletRequest req) {
        log.error("unhandled error on {}", req.getRequestURI(), ex);
        return envelope(500, "INTERNAL_ERROR", "An unexpected error occurred", null);
    }

    private Map<String, String> fieldIssue(FieldError error) {
        return Map.of("path", error.getField(), "message", error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage());
    }

    private ResponseEntity<Map<String, Object>> envelope(int status, String code, String message, Object details) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        if (details != null) {
            error.put("details", details);
        }
        return ResponseEntity.status(HttpStatus.valueOf(status)).body(Map.of("error", error));
    }
}
