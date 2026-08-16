--liquibase formatted sql
-- Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.

--changeset adam.puchala:003
CREATE TABLE financial_operation_result_outbox (
    event_id UUID PRIMARY KEY,
    operation_id UUID NOT NULL UNIQUE REFERENCES financial_operations(operation_id),
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ NULL,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'PROCESSED')),
    CONSTRAINT ck_outbox_attempts CHECK (attempts >= 0)
);

CREATE INDEX idx_outbox_status_created ON financial_operation_result_outbox(status, created_at);
