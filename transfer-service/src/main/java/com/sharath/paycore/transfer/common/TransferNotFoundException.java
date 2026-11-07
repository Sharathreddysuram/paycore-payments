package com.sharath.paycore.transfer.common;

import java.util.UUID;

/** Thrown when a transfer id does not exist in this service's database. */
public class TransferNotFoundException extends RuntimeException {

    public TransferNotFoundException(UUID id) {
        super("Transfer not found: " + id);
    }
}
