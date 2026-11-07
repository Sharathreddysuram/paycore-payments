package com.sharath.paycore.account.common;

import org.springframework.http.HttpStatus;

import java.time.Instant;

public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {

    public static ApiError of(HttpStatus status, String error, String message, String path) {
        return new ApiError(
                Instant.now(),
                status.value(),
                error,
                message,
                path
        );
    }
}
