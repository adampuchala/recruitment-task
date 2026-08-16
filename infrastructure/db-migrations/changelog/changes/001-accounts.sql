--liquibase formatted sql
-- Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.

--changeset adam.puchala:001
CREATE TABLE user_accounts (
    account_id UUID PRIMARY KEY,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_user_accounts_balance CHECK (balance >= 0),
    CONSTRAINT ck_user_accounts_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'CLOSED'))
);

CREATE TABLE create_account_idempotency_store (
    idempotency_key UUID PRIMARY KEY,
    account_id UUID NULL REFERENCES user_accounts(account_id),
    request_hash VARCHAR(64) NOT NULL,
    response_payload JSONB NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_account_idempotency_status CHECK (status IN ('PENDING', 'SUCCESS', 'ERROR'))
);
