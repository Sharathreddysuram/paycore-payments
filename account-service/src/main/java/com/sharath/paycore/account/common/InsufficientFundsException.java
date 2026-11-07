package com.sharath.paycore.account.common;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {

    public InsufficientFundsException(String accountNumber, BigDecimal balance, BigDecimal amount) {
        super("Insufficient funds on account " + accountNumber
                + ": available " + balance
                + ", requested " + amount);
    }
}
