package com.sharath.paycore.transfer.client;

import java.math.BigDecimal;

/** Request body for account-service's internal debit/credit endpoints. */
public record AmountRequest(
        BigDecimal amount
) {
}
