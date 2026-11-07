package com.sharath.paycore.transfer.client;

import java.math.BigDecimal;

/** Response body of account-service's internal debit/credit endpoints. */
public record BalanceResponse(
        String accountNumber,
        BigDecimal newBalance
) {
}
