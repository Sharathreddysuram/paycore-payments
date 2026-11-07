package com.sharath.paycore.transfer.client;

/** Thrown when account-service rejects a debit (422): insufficient funds or inactive account. */
public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String message) {
        super(message);
    }
}
