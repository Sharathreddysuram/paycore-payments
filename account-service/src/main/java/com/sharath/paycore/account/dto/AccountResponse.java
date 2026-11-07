package com.sharath.paycore.account.dto;

import com.sharath.paycore.account.Account;
import com.sharath.paycore.account.AccountStatus;
import com.sharath.paycore.account.AccountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String accountNumber,
        String ownerName,
        AccountType type,
        BigDecimal balance,
        String currency,
        AccountStatus status,
        Instant createdAt
) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getOwnerName(),
                account.getType(),
                account.getBalance(),
                account.getCurrency(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}
