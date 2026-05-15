package com.rideon.exception;

import java.time.Instant;
import java.util.Map;

public record ErrorResponse(
        int status,
        String message,
        Map<String, String> errors,
        Instant timestamp
) {
    public ErrorResponse(int status, String message) {
        this(status, message, null, Instant.now());
    }

    public ErrorResponse(int status, String message, Map<String, String> errors) {
        this(status, message, errors, Instant.now());
    }
}