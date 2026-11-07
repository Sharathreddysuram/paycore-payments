package com.sharath.paycore.transfer.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Double-entry style audit record for a completed transfer. Every completed
 * transfer writes exactly two rows: a DEBIT on the source account and a CREDIT
 * on the destination account. {@code transferId} is a plain UUID column, not a
 * JPA association, keeping this module decoupled from the {@link com.sharath.paycore.transfer.Transfer}
 * mapping even though both live in the same database.
 */
@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(name = "account_number", length = 20, nullable = false)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", length = 10, nullable = false)
    private EntryType entryType;

    @Column(name = "amount", precision = 19, scale = 4, nullable = false)
    private BigDecimal amount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** JPA only. */
    protected LedgerEntry() {
    }

    public LedgerEntry(UUID transferId, String accountNumber, EntryType entryType, BigDecimal amount) {
        this.id = UUID.randomUUID();
        this.transferId = transferId;
        this.accountNumber = accountNumber;
        this.entryType = entryType;
        this.amount = amount;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTransferId() {
        return transferId;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
