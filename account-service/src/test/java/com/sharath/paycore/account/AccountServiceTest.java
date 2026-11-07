package com.sharath.paycore.account;

import com.sharath.paycore.account.Account;
import com.sharath.paycore.account.AccountRepository;
import com.sharath.paycore.account.AccountService;
import com.sharath.paycore.account.AccountType;
import com.sharath.paycore.account.dto.AccountResponse;
import com.sharath.paycore.account.dto.BalanceResponse;
import com.sharath.paycore.account.dto.CreateAccountRequest;
import com.sharath.paycore.account.common.AccountNotFoundException;
import com.sharath.paycore.account.common.InsufficientFundsException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(AccountService.class)
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    private String createAccount(String owner, BigDecimal initialDeposit) {
        AccountResponse response = accountService.createAccount(
                new CreateAccountRequest(owner, AccountType.CHECKING, "USD", initialDeposit));
        return response.accountNumber();
    }

    @Test
    void createAccount_persistsWithGenerated12DigitNumberAndInitialDeposit() {
        AccountResponse response = accountService.createAccount(
                new CreateAccountRequest("Sharath Kumar Suram", AccountType.CHECKING, "USD",
                        new BigDecimal("1000.00")));

        assertThat(response.accountNumber()).matches("\\d{12}");
        assertThat(response.ownerName()).isEqualTo("Sharath Kumar Suram");
        assertThat(response.type()).isEqualTo(AccountType.CHECKING);
        assertThat(response.balance()).isEqualByComparingTo("1000.00");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.id()).isNotNull();
        assertThat(response.createdAt()).isNotNull();

        Account persisted = accountRepository.findByAccountNumber(response.accountNumber())
                .orElseThrow();
        assertThat(persisted.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(persisted.getStatus().name()).isEqualTo("ACTIVE");
    }

    @Test
    void debit_reducesBalance() {
        String number = createAccount("Debit User", new BigDecimal("500.00"));

        BalanceResponse response = accountService.debit(number, new BigDecimal("120.50"));

        assertThat(response.accountNumber()).isEqualTo(number);
        assertThat(response.newBalance()).isEqualByComparingTo("379.50");

        Account persisted = accountRepository.findByAccountNumber(number).orElseThrow();
        assertThat(persisted.getBalance()).isEqualByComparingTo("379.50");
    }

    @Test
    void debit_moreThanBalance_throwsAndLeavesBalanceUnchanged() {
        String number = createAccount("Broke User", new BigDecimal("100.00"));

        assertThatThrownBy(() -> accountService.debit(number, new BigDecimal("250.00")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining(number)
                .hasMessageContaining("100.00")
                .hasMessageContaining("250.00");

        Account persisted = accountRepository.findByAccountNumber(number).orElseThrow();
        assertThat(persisted.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void credit_increasesBalance() {
        String number = createAccount("Credit User", new BigDecimal("200.00"));

        BalanceResponse response = accountService.credit(number, new BigDecimal("75.25"));

        assertThat(response.accountNumber()).isEqualTo(number);
        assertThat(response.newBalance()).isEqualByComparingTo("275.25");

        Account persisted = accountRepository.findByAccountNumber(number).orElseThrow();
        assertThat(persisted.getBalance()).isEqualByComparingTo("275.25");
    }

    @Test
    void debit_missingAccount_throwsAccountNotFoundException() {
        assertThatThrownBy(() -> accountService.debit("000000000000", new BigDecimal("10.00")))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining("000000000000");
    }
}
