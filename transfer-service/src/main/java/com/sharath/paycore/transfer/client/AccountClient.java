package com.sharath.paycore.transfer.client;

import java.math.BigDecimal;

/**
 * Contract for talking to account-service's internal ledger endpoints.
 * Resolved through Eureka via a load-balanced {@code RestClient}.
 */
public interface AccountClient {

    /** Debits the given amount from the account; throws on insufficient funds / missing account. */
    BalanceResponse debit(String accountNumber, BigDecimal amount);

    /** Credits the given amount to the account; throws on missing account. */
    BalanceResponse credit(String accountNumber, BigDecimal amount);
}
