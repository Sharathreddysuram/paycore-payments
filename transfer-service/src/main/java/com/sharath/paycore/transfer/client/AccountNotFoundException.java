package com.sharath.paycore.transfer.client;

/** Thrown when account-service reports the account does not exist (404). */
public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String accountNumber) {
        super("Account not found: " + accountNumber);
    }
}
