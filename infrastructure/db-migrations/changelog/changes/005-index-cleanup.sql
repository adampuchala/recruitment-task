--liquibase formatted sql
-- Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.

--changeset adam.puchala:005
-- Supports account history lookup and ordering for operations that debit an account.
COMMENT ON INDEX idx_financial_operations_from_created IS
    'Supports account history lookup ordered by creation time for debited accounts.';

-- Supports account history lookup and ordering for operations that credit an account.
COMMENT ON INDEX idx_financial_operations_to_created IS
    'Supports account history lookup ordered by creation time for credited accounts.';

-- Supports efficient polling of pending Outbox events in creation order.
COMMENT ON INDEX idx_outbox_status_created IS
    'Supports Outbox polling by status in creation order.';

-- No current query lists all financial operations ordered only by creation time.
DROP INDEX idx_financial_operations_created;
