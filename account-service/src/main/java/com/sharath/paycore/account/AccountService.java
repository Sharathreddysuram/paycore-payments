package com.sharath.paycore.account;

import com.sharath.paycore.account.dto.AccountResponse;
import com.sharath.paycore.account.dto.BalanceResponse;
import com.sharath.paycore.account.dto.CreateAccountRequest;
import com.sharath.paycore.account.common.AccountNotFoundException;
import com.sharath.paycore.account.common.InsufficientFundsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Service
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        String accountNumber = generateAccountNumber();
        Account account = new Account(
                accountNumber,
                request.ownerName(),
                request.type(),
                request.initialDeposit(),
                request.currency()
        );
        Account saved = accountRepository.save(account);
        return AccountResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponse getByNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
        return AccountResponse.from(account);
    }

    @Transactional
    public BalanceResponse debit(String accountNumber, BigDecimal amount) {
        Account account = findLocked(accountNumber);
        requireActive(account);
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException(accountNumber, account.getBalance(), amount);
        }
        BigDecimal newBalance = account.getBalance().subtract(amount);
        account.setBalance(newBalance);
        return new BalanceResponse(accountNumber, newBalance);
    }

    @Transactional
    public BalanceResponse credit(String accountNumber, BigDecimal amount) {
        Account account = findLocked(accountNumber);
        requireActive(account);
        BigDecimal newBalance = account.getBalance().add(amount);
        account.setBalance(newBalance);
        return new BalanceResponse(accountNumber, newBalance);
    }

    private Account findLocked(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    private void requireActive(Account account) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Account is not ACTIVE: " + account.getAccountNumber());
        }
    }

    private String generateAccountNumber() {
        long value = Math.abs(RANDOM.nextLong()) % 1_000_000_000_000L;
        return String.format("%012d", value);
    }
}
