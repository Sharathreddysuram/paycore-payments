package com.sharath.paycore.account.internal;

import com.sharath.paycore.account.AccountService;
import com.sharath.paycore.account.dto.AmountRequest;
import com.sharath.paycore.account.dto.BalanceResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cluster-internal endpoints used by the transfer-service. These are never
 * routed through the API gateway and carry no auth of their own; they must
 * only be reachable inside the cluster.
 */
@RestController
@RequestMapping("/internal/accounts")
public class InternalAccountController {

    private final AccountService accountService;

    public InternalAccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/{accountNumber}/debit")
    public BalanceResponse debit(@PathVariable String accountNumber,
                                 @Valid @RequestBody AmountRequest request) {
        return accountService.debit(accountNumber, request.amount());
    }

    @PostMapping("/{accountNumber}/credit")
    public BalanceResponse credit(@PathVariable String accountNumber,
                                  @Valid @RequestBody AmountRequest request) {
        return accountService.credit(accountNumber, request.amount());
    }
}
