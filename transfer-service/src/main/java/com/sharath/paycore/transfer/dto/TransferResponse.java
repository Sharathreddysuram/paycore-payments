package com.sharath.paycore.transfer.dto;

import com.sharath.paycore.transfer.Transfer;
import com.sharath.paycore.transfer.TransferStatus;

import java.math.BigDecimal;
import java.util.UUID;

/** Outbound view of a transfer. */
public record TransferResponse(
        UUID transferId,
        String idempotencyKey,
        TransferStatus status,
        BigDecimal amount,
        String failureReason
) {

    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getIdempotencyKey(),
                transfer.getStatus(),
                transfer.getAmount(),
                transfer.getFailureReason()
        );
    }
}
