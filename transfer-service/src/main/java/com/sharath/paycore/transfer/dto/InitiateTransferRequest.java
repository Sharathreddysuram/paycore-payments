package com.sharath.paycore.transfer.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/** Request body for initiating a money transfer. */
public record InitiateTransferRequest(
        @NotBlank(message = "idempotencyKey is required") String idempotencyKey,
        @NotBlank(message = "fromAccountNumber is required") String fromAccountNumber,
        @NotBlank(message = "toAccountNumber is required") String toAccountNumber,
        @NotNull(message = "amount is required") @DecimalMin(value = "0.01", message = "amount must be at least 0.01") BigDecimal amount,
        @NotBlank(message = "currency is required") @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter ISO code (e.g. USD)") String currency
) {
}
