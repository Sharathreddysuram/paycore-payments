CREATE TABLE transfer (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(64) NOT NULL UNIQUE,
    from_account_number VARCHAR(20) NOT NULL,
    to_account_number VARCHAR(20) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_transfer_idempotency ON transfer(idempotency_key);

CREATE TABLE ledger_entry (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL,
    account_number VARCHAR(20) NOT NULL,
    entry_type VARCHAR(10) NOT NULL,
    amount NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_ledger_transfer ON ledger_entry(transfer_id);
