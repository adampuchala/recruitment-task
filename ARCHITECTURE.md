# Bank System Architecture

Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.

## Architecture diagram

The current high-level architecture is illustrated in [bank_4.drawio.svg](docs/bank_4.drawio.svg).

The mobile BFF is optional. The initial implementation can expose the REST API directly to Postman and, later, to the native mobile client.

## Goals and boundaries

The system supports:

- account creation, retrieval and status changes;
- deposits, withdrawals and transfers;
- prevention of negative balances;
- safe concurrent financial operations;
- idempotent account and financial-operation requests;
- synchronous operation history;
- asynchronous audit processing through Kafka;
- retry and duplicate-event handling.

The solution uses one PostgreSQL database for the project. The services are separated by responsibility and should modify only the tables owned by their module.

## Services

### Account Service

Responsible for account lifecycle:

- create an account;
- retrieve account details;
- change account status.

It owns `user_accounts`. Account status rules:

- `ACTIVE` allows financial operations;
- `BLOCKED` prevents deposits, withdrawals and transfers;
- `CLOSED` is final and prevents further operations;
- an account can be closed only when its balance is zero.

### Financial Operations Service

Responsible for deposits, withdrawals and transfers. It validates the account status and balance, locks the required account rows, updates balances, stores the operation history and creates an outbox event in one database transaction.

For transfers, both accounts are locked in deterministic order by `account_id`. This prevents race conditions and avoids deadlocks caused by two simultaneous transfers in opposite directions.

### Outbox Worker

Polls `financial_operation_result_outbox` for pending events and publishes them to Kafka. An event is marked `PROCESSED` only after successful publication. Failed attempts are retried and can eventually be moved to a dead-letter flow or marked as failed.

### Kafka Consumer

Consumes successful financial-operation events and writes an audit record to `financial_operations_audit_events`. The consumer is idempotent: `event_id` is unique and the audit insert is performed transactionally.

## Database model

### `user_accounts`

Source of truth for the current account state and balance.

| Column | Description |
|---|---|
| `account_id` | UUID primary key |
| `first_name` | Account holder first name |
| `last_name` | Account holder last name |
| `balance` | `NUMERIC(19,4)`, must be non-negative |
| `status` | `ACTIVE`, `BLOCKED` or `CLOSED` |
| `version` | Optimistic-locking version; optional when using row locks |
| `created_at` | Creation timestamp |
| `updated_at` | Last update timestamp |

### `create_account_idempotency_store`

Stores the result of an account-creation request.

| Column | Description |
|---|---|
| `idempotency_key` | UUID primary key / unique request key |
| `account_id` | Created account identifier |
| `request_hash` | Detects reuse of a key with a different request |
| `response_payload` | Original successful or error response |
| `status` | `PENDING`, `SUCCESS` or `ERROR` |
| `created_at` | Creation timestamp |

### `financial_operations`

The synchronous source of truth for operation history. It is written before the API returns a successful financial-operation response.

| Column | Description |
|---|---|
| `operation_id` | UUID primary key |
| `type` | `DEPOSIT`, `WITHDRAWAL` or `TRANSFER` |
| `from_account_id` | Source account; nullable for deposits |
| `to_account_id` | Destination account; nullable for withdrawals |
| `amount` | Positive `NUMERIC(19,4)` value |
| `status` | `SUCCESS` or `REJECTED` |
| `description` | Optional operation description |
| `created_at` | Operation timestamp |

### `financial_operations_idempotency_store`

Maps a client-provided `Idempotency-Key` to the resulting financial operation.

| Column | Description |
|---|---|
| `idempotency_key` | UUID primary key / unique request key |
| `operation_id` | Resulting operation identifier |
| `request_hash` | Hash of the request body and relevant parameters |
| `response_payload` | Original response returned to the client |
| `status` | `PENDING`, `SUCCESS` or `ERROR` |
| `created_at` | Creation timestamp |

### `financial_operation_result_outbox`

Stores events atomically with successful financial operations.

| Column | Description |
|---|---|
| `event_id` | UUID primary key |
| `operation_id` | Related operation identifier |
| `payload` | Serialized event payload |
| `status` | `PENDING` or `PROCESSED` |
| `attempts` | Number of publication attempts |
| `created_at` | Event creation timestamp |
| `processed_at` | Successful publication timestamp |

### `financial_operations_audit_events`

Asynchronous audit projection written by the Kafka consumer.

| Column | Description |
|---|---|
| `event_id` | UUID primary key and unique deduplication key |
| `operation_id` | Related financial operation |
| `from_account_id` | Source account, when applicable |
| `to_account_id` | Destination account, when applicable |
| `amount` | Operation amount |
| `created_at` | Audit timestamp |

## Transaction and event flow

```text
Client
  |
  v
Financial Operations API
  |
  | database transaction
  | - lock account rows
  | - validate status and balance
  | - update user_accounts
  | - insert financial_operations
  | - insert outbox event
  v
PostgreSQL COMMIT
  |
  v
Outbox Worker -> Kafka -> Audit Consumer -> financial_operations_audit_events
```

The operation history is not dependent on Kafka availability. Eventual consistency exists only between the committed operation and its asynchronous audit record.

## Kafka contract

Topic:

```text
financial-operations
```

Partition key:

```text
operation_id
```

Event name:

```text
FinancialOperationCompleted
```

Example event:

```json
{
  "eventId": "f4f5b7e7-9f17-4f4a-ae5f-9f0e7d7e4df1",
  "eventType": "FINANCIAL_OPERATION_COMPLETED",
  "operationId": "d09c1d2a-6ca0-4cb4-982f-2a0a8c5adf9d",
  "operationType": "TRANSFER",
  "fromAccountId": "8a7c9f0d-1b6a-4f2f-9e7f-52e2f9e7d1a2",
  "toAccountId": "1c1be0f1-5ad9-4b14-83ea-5f18f2e4cc4d",
  "amount": 100.00,
  "occurredAt": "2026-08-16T12:00:00Z"
}
```

The consumer retries processing failures. A duplicate event is ignored when its `event_id` already exists in `financial_operations_audit_events`.

## API design

All endpoints are versioned under `/api/v1`.

### Create account

```http
POST /api/v1/accounts
Idempotency-Key: 1a2b3c4d-0000-0000-0000-000000000001
Content-Type: application/json
```

```json
{
  "firstName": "Anna",
  "lastName": "Kowalska"
}
```

Response `201 Created`:

```json
{
  "accountId": "8a7c9f0d-1b6a-4f2f-9e7f-52e2f9e7d1a2",
  "firstName": "Anna",
  "lastName": "Kowalska",
  "balance": 0.00,
  "status": "ACTIVE",
  "createdAt": "2026-08-16T12:00:00Z"
}
```

Repeating the request with the same `Idempotency-Key` returns the same account response and does not create another account.

### Get account

```http
GET /api/v1/accounts/{accountId}
```

Response `200 OK`:

```json
{
  "accountId": "8a7c9f0d-1b6a-4f2f-9e7f-52e2f9e7d1a2",
  "firstName": "Anna",
  "lastName": "Kowalska",
  "balance": 900.00,
  "status": "ACTIVE",
  "updatedAt": "2026-08-16T12:05:00Z"
}
```

### Change account status

```http
PUT /api/v1/accounts/{accountId}/status
Content-Type: application/json
```

```json
{
  "status": "BLOCKED"
}
```

Response `200 OK` returns the updated account.

### Deposit

```http
POST /api/v1/accounts/{accountId}/deposits
Idempotency-Key: 2a2b3c4d-0000-0000-0000-000000000001
Content-Type: application/json
```

```json
{
  "amount": 250.00,
  "description": "Initial deposit"
}
```

Response `201 Created`:

```json
{
  "operationId": "d09c1d2a-6ca0-4cb4-982f-2a0a8c5adf9d",
  "type": "DEPOSIT",
  "status": "SUCCESS",
  "amount": 250.00,
  "balanceAfter": 1150.00,
  "createdAt": "2026-08-16T12:05:00Z"
}
```

### Withdrawal

```http
POST /api/v1/accounts/{accountId}/withdrawals
Idempotency-Key: 3a2b3c4d-0000-0000-0000-000000000001
Content-Type: application/json
```

```json
{
  "amount": 100.00,
  "description": "ATM withdrawal"
}
```

The request is rejected if the account is not `ACTIVE` or the withdrawal would make the balance negative.

### Transfer

```http
POST /api/v1/transfers
Idempotency-Key: 4a2b3c4d-0000-0000-0000-000000000001
Content-Type: application/json
```

```json
{
  "fromAccountId": "8a7c9f0d-1b6a-4f2f-9e7f-52e2f9e7d1a2",
  "toAccountId": "1c1be0f1-5ad9-4b14-83ea-5f18f2e4cc4d",
  "amount": 100.00,
  "description": "Transfer"
}
```

Response `201 Created`:

```json
{
  "operationId": "d09c1d2a-6ca0-4cb4-982f-2a0a8c5adf9d",
  "type": "TRANSFER",
  "status": "SUCCESS",
  "fromAccountId": "8a7c9f0d-1b6a-4f2f-9e7f-52e2f9e7d1a2",
  "toAccountId": "1c1be0f1-5ad9-4b14-83ea-5f18f2e4cc4d",
  "amount": 100.00,
  "createdAt": "2026-08-16T12:10:00Z"
}
```

### Operation history

```http
GET /api/v1/accounts/{accountId}/operations
GET /api/v1/operations/{operationId}
```

Example response:

```json
{
  "items": [
    {
      "operationId": "d09c1d2a-6ca0-4cb4-982f-2a0a8c5adf9d",
      "type": "TRANSFER",
      "fromAccountId": "8a7c9f0d-1b6a-4f2f-9e7f-52e2f9e7d1a2",
      "toAccountId": "1c1be0f1-5ad9-4b14-83ea-5f18f2e4cc4d",
      "amount": 100.00,
      "status": "SUCCESS",
      "createdAt": "2026-08-16T12:10:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

### Error response

All errors use a consistent shape:

```json
{
  "code": "INSUFFICIENT_FUNDS",
  "message": "The account balance is insufficient for this operation",
  "timestamp": "2026-08-16T12:11:00Z",
  "path": "/api/v1/accounts/8a7c9f0d-1b6a-4f2f-9e7f-52e2f9e7d1a2/withdrawals"
}
```

Suggested status codes:

| Status | Meaning |
|---|---|
| `201` | Account or financial operation created |
| `200` | Account or history retrieved/updated |
| `400` | Invalid request or non-positive amount |
| `404` | Account or operation not found |
| `409` | Insufficient funds, blocked account, idempotency-key conflict or invalid status transition |
| `500` | Unexpected server error |

## Consistency guarantees

- A financial operation, balance update, history record and outbox event commit together.
- Negative balances are prevented inside the database transaction while the account rows are locked.
- Repeated requests with the same idempotency key return the original result.
- Kafka delivery is at-least-once; the consumer is idempotent by `event_id`.
- Audit events are eventually consistent with the committed financial operation.

## Architectural trade-offs

### Shared database

The services use one shared PostgreSQL database. In a production microservices architecture, each service would normally own its data and communicate through APIs or events. We chose a shared database because of the time limit of the exercise and because account status, balances, operation history and the outbox must be updated consistently.

The logical ownership of tables is still separated by module. Shared physical storage does not mean that every service should modify every table.

### No separate read-heavy account model and write-heavy ledger model

The system does not split account details and accounting data into separate read and write models. `user_accounts` stores the current balance and account state, `financial_operations` stores the synchronous operation history, and `financial_operations_audit_events` is an asynchronous audit projection.

A production system could introduce a dedicated read model for account details and a write-optimized, append-only ledger. That would improve independent scaling and provide stronger accounting separation, but it would also introduce more synchronization, reconciliation and eventual-consistency concerns. A full double-entry ledger is intentionally outside the scope of this exercise.

### No CQRS

The system uses the same domain model for commands and queries. We did not introduce separate command and query models because CQRS would require additional design decisions around projections, event versioning, rebuilds, synchronization, storage and eventual consistency.

The current design keeps the core financial transaction strongly consistent and uses Kafka only for the additional asynchronous audit action. This gives a simpler implementation while preserving the important guarantee that balance changes and financial-operation history commit atomically.

### No Redis cache

The system does not use Redis for account or operation reads. A cache could reduce database load for read-heavy account-details requests, but it would require cache invalidation and a clear consistency policy after deposits, withdrawals, transfers and status changes.

Caching was intentionally omitted because of the exercise time limit and because the expected workload does not justify the additional infrastructure. If read traffic increased, Redis could be added around a dedicated account-details read model, preferably as part of a broader CQRS/read-model evolution.

### No Schema Registry

Kafka messages use JSON payloads without a Schema Registry. This reduces infrastructure and setup complexity for the exercise, but it also means that schema compatibility, required fields and event versioning must be handled by the application code and tests.

For a production system with multiple independently deployed producers and consumers, a Schema Registry with Avro, Protobuf or JSON Schema would provide stronger compatibility guarantees. The current implementation should still include an `eventType` and should keep the event contract explicit and backwards-compatible where practical.

### No authentication or authorization

Authentication and authorization are intentionally omitted from this recruitment MVP. Adding a partial identity and permission model would increase scope without improving the evaluation of the required account, transaction, concurrency, idempotency and Kafka behavior. A production implementation would add authentication, authorization and audit requirements before exposing the API publicly.

### Overall assessment

These trade-offs prioritize a correct and demonstrable financial core over production-scale distribution. The design keeps the balance update, operation history and outbox event in one transaction, while limiting asynchronous processing to audit events. This is appropriate for the scope of the exercise; a production version would likely move toward database-per-service ownership, a dedicated ledger, CQRS/read projections and selective caching.

## Operational conventions

- The implementation is written in Kotlin on Java 25.
- A dedicated `db-migrations` Docker Compose service applies Liquibase changesets before application services start. Domain-model changes must be accompanied by new Liquibase changesets.
- Configuration is supplied through environment variables; secrets are not committed.
- API and event timestamps use ISO-8601 in UTC.
- Money is represented with decimal types (`BigDecimal`/PostgreSQL `NUMERIC`), never floating-point types.
- Application JARs are built first with `./gradlew clean build`; the complete runtime environment is then started with `docker compose up --build`. Dockerfiles do not run Gradle.
- Logs are minimal and structured around operation and event identifiers. Sensitive information must never be logged.
