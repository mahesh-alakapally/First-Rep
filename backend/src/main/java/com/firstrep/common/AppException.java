package com.firstrep.common;

import org.springframework.http.HttpStatus;

public class AppException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public AppException(HttpStatus status, String code, String detail) {
        super(detail);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static AppException notFound(String detail) {
        return new AppException(HttpStatus.NOT_FOUND, "NOT_FOUND", detail);
    }

    public static AppException forbidden(String detail) {
        return new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", detail);
    }

    public static AppException bad(String code, String detail) {
        return new AppException(HttpStatus.BAD_REQUEST, code, detail);
    }

    public static AppException conflict(String code, String detail) {
        return new AppException(HttpStatus.CONFLICT, code, detail);
    }

    public static AppException paymentRequired(String detail) {
        return new AppException(HttpStatus.PAYMENT_REQUIRED, "PREMIUM_REQUIRED", detail);
    }

    public static AppException unauthorized() {
        return new AppException(HttpStatus.UNAUTHORIZED, "AUTH_REQUIRED", "Sign in required.");
    }
}
