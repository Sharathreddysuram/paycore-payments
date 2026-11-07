package com.sharath.paycore.account.dto;

import java.math.BigDecimal;

public record BalanceResponse(
        String accountNumber,
        BigDecimal newBalance
) {
}
