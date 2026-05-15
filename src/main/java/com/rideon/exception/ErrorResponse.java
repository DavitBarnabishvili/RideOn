package com.rideon.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
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