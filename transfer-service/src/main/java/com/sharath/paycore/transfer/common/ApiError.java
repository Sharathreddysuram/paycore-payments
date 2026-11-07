package com.sharath.paycore.transfer.common;

import java.time.Instant;

/** Standard error body returned by {@link GlobalExceptionHandler}. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message
) {

    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, message);
    }
}
