package com.sharath.paycore.transfer;

import com.sharath.paycore.transfer.client.AccountClient;
import com.sharath.paycore.transfer.client.AccountNotFoundException;
import com.sharath.paycore.transfer.client.InsufficientFundsException;
import com.sharath.paycore.transfer.common.TransferNotFoundException;
import com.sharath.paycore.transfer.ledger.EntryType;
import com.sharath.paycore.transfer.ledger.LedgerEntry;
import com.sharath.paycore.transfer.ledger.LedgerEntryRepository;
import com.sharath.paycore.transfer.dto.InitiateTransferRequest;
import com.sharath.paycore.transfer.dto.TransferResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Orchestrates money movement between accounts. This service never touches
 * balances directly — it calls account-service's internal endpoints over HTTP,
 * resolved through Eureka via a load-balanced {@code RestClient}. It persists
 * its own {@link Transfer} row plus double-entry {@link LedgerEntry} audit
 * rows, storing account numbers as plain strings (database-per-service).
 *
 * <p><b>Orchestration without distributed transactions:</b> debit and credit
 * are each atomic inside account-service, but there is NO atomicity ACROSS the
 * two calls. If credit fails after a successful debit, the money is debited
 * without a matching credit and no transfer row is persisted (an unexpected
 * {@code RuntimeException} propagates, the transaction rolls back, and the
 * client can safely retry with the same idempotency key — but the debit is
 * NOT undone). Production hardening would use the saga pattern with
 * compensating actions (credit-back) — documented in the README.
 */
@Service
public class TransferService {

    /** Result of {@link #initiate(InitiateTransferRequest)}: the response plus whether a new row was created. */
    public record InitiationResult(TransferResponse response, boolean created) {
    }

    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountClient accountClient;

    public TransferService(TransferRepository transferRepository,
                           LedgerEntryRepository ledgerEntryRepository,
                           AccountClient accountClient) {
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountClient = accountClient;
    }

    @Transactional
    public InitiationResult initiate(InitiateTransferRequest req) {
        // 1. Idempotency first: a replayed key returns the original outcome, no money moves again.
        Optional<Transfer> existing = transferRepository.findByIdempotencyKey(req.idempotencyKey());
        if (existing.isPresent()) {
            return new InitiationResult(TransferResponse.from(existing.get()), false);
        }

        // 2. Domain validation beyond bean validation.
        if (req.fromAccountNumber().equals(req.toAccountNumber())) {
            throw new IllegalArgumentException("fromAccountNumber and toAccountNumber must be different");
        }

        // 3. Orchestrate in fixed order: debit first, then credit.
        try {
            accountClient.debit(req.fromAccountNumber(), req.amount());
        } catch (InsufficientFundsException | AccountNotFoundException e) {
            Transfer failed = new Transfer(
                    req.idempotencyKey(), req.fromAccountNumber(), req.toAccountNumber(),
                    req.amount(), req.currency(), TransferStatus.FAILED, e.getMessage());
            transferRepository.save(failed);
            return new InitiationResult(TransferResponse.from(failed), true);
        }
        // Unexpected RuntimeExceptions (e.g. account-service 5xx) propagate -> 500,
        // no transfer row persisted, so the client can safely retry with the same key.
        accountClient.credit(req.toAccountNumber(), req.amount());

        // 4. Persist COMPLETED transfer + 2 ledger rows (DEBIT on source, CREDIT on destination).
        Transfer completed = new Transfer(
                req.idempotencyKey(), req.fromAccountNumber(), req.toAccountNumber(),
                req.amount(), req.currency(), TransferStatus.COMPLETED, null);
        transferRepository.save(completed);
        ledgerEntryRepository.save(
                new LedgerEntry(completed.getId(), req.fromAccountNumber(), EntryType.DEBIT, req.amount()));
        ledgerEntryRepository.save(
                new LedgerEntry(completed.getId(), req.toAccountNumber(), EntryType.CREDIT, req.amount()));

        return new InitiationResult(TransferResponse.from(completed), true);
    }

    @Transactional(readOnly = true)
    public TransferResponse getById(UUID id) {
        Transfer transfer = transferRepository.findById(id)
                .orElseThrow(() -> new TransferNotFoundException(id));
        return TransferResponse.from(transfer);
    }
}
