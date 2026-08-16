--liquibase formatted sql
-- Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.

--changeset adam.puchala:002
CREATE TABLE financial_operations (
    operation_id UUID PRIMARY KEY,
    type VARCHAR(20) NOT NULL,
    from_account_id UUID NULL REFERENCES user_accounts(account_id),
    to_account_id UUID NULL REFERENCES user_accounts(account_id),
    amount NUMERIC(19,4) NOT NULL,
    status VARCHAR(20) NOT NULL,
    description VARCHAR(500) NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_financial_operation_amount CHECK (amount > 0),
    CONSTRAINT ck_financial_operation_type CHECK (type IN ('DEPOSIT', 'WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT ck_financial_operation_status CHECK (status IN ('SUCCESS', 'REJECTED')),
    CONSTRAINT ck_financial_operation_accounts CHECK (
        (type = 'DEPOSIT' AND from_account_id IS NULL AND to_account_id IS NOT NULL) OR
        (type = 'WITHDRAWAL' AND from_account_id IS NOT NULL AND to_account_id IS NULL) OR
        (type = 'TRANSFER' AND from_account_id IS NOT NULL AND to_account_id IS NOT NULL AND from_account_id <> to_account_id)
    )
);

CREATE INDEX idx_financial_operations_from_created ON financial_operations(from_account_id, created_at DESC);
CREATE INDEX idx_financial_operations_to_created ON financial_operations(to_account_id, created_at DESC);
CREATE INDEX idx_financial_operations_created ON financial_operations(created_at DESC);

CREATE TABLE financial_operations_idempotency_store (
    idempotency_key UUID PRIMARY KEY,
    operation_id UUID NULL REFERENCES financial_operations(operation_id),
    request_hash VARCHAR(64) NOT NULL,
    response_payload JSONB NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_financial_idempotency_status CHECK (status IN ('PENDING', 'SUCCESS', 'ERROR'))
);
