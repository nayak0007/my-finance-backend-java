package com.finance.tracker.exception;

public class AppException extends RuntimeException {
    private final String code;
    private final int status;
    private final Object details;

    public AppException(String code, String message, int status) {
        this(code, message, status, null);
    }

    public AppException(String code, String message, int status, Object details) {
        super(message);
        this.code = code;
        this.status = status;
        this.details = details;
    }

    public String getCode() {
        return code;
    }

    public int getStatus() {
        return status;
    }

    public Object getDetails() {
        return details;
    }

    public static AppException unauthorized() {
        return unauthorized("Missing or invalid authentication token");
    }

    public static AppException unauthorized(String message) {
        return new AppException("UNAUTHORIZED", message, 401);
    }

    public static AppException forbidden(String message) {
        return new AppException("FORBIDDEN", message, 403);
    }

    public static AppException notFound(String message) {
        return new AppException("NOT_FOUND", message, 404);
    }

    public static AppException badRequest(String message) {
        return new AppException("BAD_REQUEST", message, 400);
    }

    public static AppException validation(String message, Object details) {
        return new AppException("VALIDATION_ERROR", message, 400, details);
    }

    public static AppException conflict(String message) {
        return new AppException("CONFLICT", message, 409);
    }

    public static AppException upstream(String message) {
        return new AppException("UPSTREAM_ERROR", message, 502);
    }
}
