--liquibase formatted sql
-- Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.

--changeset adam.puchala:004
CREATE TABLE financial_operations_audit_events (
    event_id UUID PRIMARY KEY,
    operation_id UUID NOT NULL,
    from_account_id UUID NULL,
    to_account_id UUID NULL,
    amount NUMERIC(19,4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_audit_amount CHECK (amount > 0)
);
