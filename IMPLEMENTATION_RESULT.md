# Implementation Result

Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.

## Summary

The complete mandatory backend described in [AGENTS.md](AGENTS.md), [ARCHITECTURE.md](ARCHITECTURE.md) and [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) has been implemented.

The solution consists of:

- Account Service for account creation, retrieval, lifecycle management and account-creation idempotency;
- Financial Operations Service for deposits, withdrawals, transfers and synchronous operation history;
- Outbox Worker for coroutine-based, non-blocking publication of committed financial-operation events;
- Audit Consumer for eventually consistent, deduplicated audit storage;
- PostgreSQL with centrally managed Liquibase migrations;
- Redpanda as the Kafka-compatible broker;
- Docker Compose, static OpenAPI specifications, Swagger UI and a runnable Postman collection.

The optional mobile application and BFF were intentionally not implemented because they are outside the mandatory backend scope.

## Services and infrastructure

| Component | Responsibility | Port |
| --- | --- | ---: |
| Account Service | Account creation, retrieval and status changes | 8081 |
| Financial Operations Service | Deposits, withdrawals, transfers and history | 8082 |
| Outbox Worker | Outbox polling and Kafka publication | 8083 |
| Audit Consumer | Kafka consumption, deduplication, retry and DLT | 8084 |
| PostgreSQL | Shared operational and audit database | 5432 |
| Redpanda | Kafka-compatible messaging | 19092 |
| `db-migrations` | Central Liquibase migration runner | one-shot |
| `redpanda-init` | Idempotent topic creation | one-shot |

The Redpanda initialization creates:

- `financial-operations` with three partitions and replication factor one;
- `financial-operations.DLT` with three partitions and replication factor one.

## Key implementation decisions

- Kotlin, Java 25 and Spring Boot 4.1.0 are used consistently.
- Kotlin coroutines are used in controllers, application services, repository ports, Kafka consumer handling and the Outbox scheduler; Reactor remains only as an internal Spring/WebFlux/R2DBC bridge.
- HTTP services use Spring WebFlux and Netty.
- Runtime database access uses Spring Data R2DBC and the PostgreSQL R2DBC driver.
- There is no JPA, Hibernate ORM, WebMVC or blocking JDBC in application code.
- `R2dbcTransactionManager` and `TransactionalOperator.executeAndAwait` define explicit coroutine-aware reactive transaction boundaries.
- Account rows are locked with `SELECT ... FOR UPDATE` on the transaction-bound R2DBC connection.
- Transfers lock account UUIDs in deterministic order to prevent deadlocks.
- Balance mutation, successful history, idempotency state and Outbox insertion commit atomically.
- Rejected operations do not create history or Outbox records.
- Persisted request hashes prevent reuse of an idempotency key for a different request.
- The Outbox Worker publishes only committed events and marks records processed only after broker acknowledgement.
- Kafka delivery is at least once. The Audit Consumer deduplicates with the unique `event_id` primary key.
- Consumer failures are retried three times with one-second backoff before publication to the DLT.
- Operation history is read directly from `financial_operations` and is immediately available independently of Kafka.
- Audit data is the only eventually consistent projection.
- Money uses `BigDecimal` and PostgreSQL `NUMERIC(19,4)`.
- API and event timestamps use ISO-8601 UTC.

## Coroutine migration result

The implementation was migrated according to [COROUTINES_MIGRATION_PLAN.md](COROUTINES_MIGRATION_PLAN.md). The migration did not change REST, database, Kafka or Docker contracts.

- Account and Financial Operations repository ports now expose suspending functions.
- R2DBC adapters use coroutine await extensions and bounded `List` results.
- Financial transactions preserve the same connection, row-locking order and atomic write sequence through `executeAndAwait`.
- Audit Consumer uses a suspending `@KafkaListener`; acknowledgement remains deferred until the transactional audit handler completes.
- Outbox Worker uses a suspending fixed-delay scheduler, sequential publication and cancellation-safe retry handling.
- The full build executed 21 tests with no failures or skips.
- No production `Mono`, `Flux`, `.block()` or manual Reactor subscription remains in the four services.

## Automated test results

The final clean build executed 21 tests with no failures and no skipped tests.

Covered scenarios include:

- account status-transition rules;
- concurrent account creation with one idempotency key;
- idempotency-key hash conflict;
- amount and insufficient-funds rules;
- concurrent withdrawals without a negative balance;
- opposite-direction concurrent transfers without a deadlock and with preserved total balance;
- concurrent financial requests sharing one idempotency key;
- atomic rollback of balance, operation, idempotency and Outbox changes;
- stable Kafka event serialization;
- successful Outbox publication and acknowledgement handling;
- Outbox failure attempt handling;
- PostgreSQL and Redpanda Testcontainers pipeline: Outbox Worker to Kafka to Audit Consumer;
- duplicate Kafka event deduplication;
- malformed-event propagation;
- three retries followed by DLT publication with the original key and payload.

The integration tests apply the same central Liquibase changelog used by Docker Compose. The database schema is not duplicated in test SQL.

## Build result

The required command completed successfully on Java 25:

```text
./gradlew clean build
BUILD SUCCESSFUL
37 actionable tasks: 37 executed
```

The build also runs static OpenAPI path/version validation and produces the required runtime JARs:

- `account-service.jar`;
- `financial-operations-service.jar`;
- `outbox-worker.jar`;
- `audit-consumer.jar`.

No production use of `.block()` was found. The resolved runtime dependency trees contain no Spring WebMVC, Spring Data JPA, Hibernate ORM, Redis, Confluent or Schema Registry dependencies.

## Docker Compose result

The documented clean flow completed successfully:

```bash
./gradlew clean build
docker compose up --build
```

Final state:

- PostgreSQL: healthy;
- Redpanda: healthy;
- Account Service: healthy;
- Financial Operations Service: healthy;
- Outbox Worker: healthy;
- Audit Consumer: healthy;
- `db-migrations`: exited with code 0;
- `redpanda-init`: exited with code 0.

A second Liquibase execution succeeded with the fifth changeset applied; subsequent executions are no-ops.

All service Dockerfiles are runtime-only. They copy an already-built JAR and do not run Gradle inside an image.

## Postman result

The repository's previously recorded Postman execution against a fresh Docker Compose environment used Newman:

```text
Requests:   16 executed, 0 failed
Assertions: 19 executed, 0 failed
```

The collection verifies:

- creation of source and destination accounts;
- deposit and idempotent deposit replay;
- transfer and withdrawal;
- balances of both accounts;
- account operation history;
- operation retrieval by ID;
- insufficient-funds rejection;
- blocked-account rejection;
- closure of a zero-balance account;
- terminal behavior of a closed account.

During the coroutine migration verification, the `newman` executable was not installed and an `npx` download did not complete in the available environment, so no new Newman result is claimed for this run. An equivalent HTTP smoke test against the running Compose stack passed 13 checks: account creation, deposit, idempotent replay, transfer, withdrawal, balances, history, operation lookup, insufficient funds and blocked-account behavior. The Postman collection itself was not changed.

## Asynchronous integration QA

The final environment confirmed the expected steady state after three successful financial operations:

```text
financial_operations:                 3
processed Outbox records:             3
pending Outbox records:               0
financial_operations_audit_events:    3
```

Additional runtime checks confirmed:

- while Redpanda was stopped, a financial operation committed and was immediately visible in history;
- its Outbox record remained `PENDING` and no audit record existed;
- after Redpanda restarted, the record changed to `PROCESSED` and one audit row appeared;
- publishing the same event twice created no additional audit row;
- a malformed event was retried three times and then appeared on `financial-operations.DLT` with its original key and payload;
- the documented DLT reprocessing procedure uses explicit manual inspection and republishing rather than an automatic retry loop.

## Swagger and OpenAPI

- Account Swagger UI is active at `http://localhost:8081/swagger-ui.html`.
- Financial Operations Swagger UI is active at `http://localhost:8082/swagger-ui.html`.
- Every runnable service contains its own `openapi.yaml`.
- The two API specifications document commands, responses, validation, pagination, idempotency and error responses.
- Worker and Consumer specifications document their health endpoints and lack of a business API.

## Definition of Done

- [x] Account creation, retrieval and status changes work.
- [x] Deposits, withdrawals and transfers work.
- [x] Negative balances are prevented under concurrent access.
- [x] Repeated requests are idempotent, including concurrent repeats.
- [x] Idempotency-key reuse with a different request is rejected.
- [x] Successful operations atomically create history and an Outbox record.
- [x] Rejected operations create no Kafka event.
- [x] Outbox events are published only after the financial transaction commits.
- [x] Outbox publication retries after failures and tolerates duplicate publication windows.
- [x] Audit consumption acknowledges only after the suspending transactional database operation completes.
- [x] Audit records are deduplicated by `event_id`.
- [x] Retry, DLT and manual reprocessing are implemented and documented.
- [x] Operation history remains independent of Kafka and immediately available.
- [x] Central Liquibase migrations work on an empty database and are idempotent.
- [x] The clean Gradle build succeeds.
- [x] The Docker Compose startup flow succeeds.
- [x] All four Spring services report healthy.
- [x] Swagger UI and static OpenAPI specifications are available.
- [x] The Postman happy path and error scenarios pass.
- [x] Focused unit, coroutine and Testcontainers integration tests pass.
- [x] Mandatory documentation and copyright notices are present.
- [x] Optional BFF, mobile, CQRS, cache and security work did not displace mandatory scope.

## Known limitations and documented trade-offs

- All services share one PostgreSQL database because of the exercise time limit.
- Account details and the write-heavy balance/operation model are not separated.
- There are no separate command and query models and no CQRS projection database.
- There is no Redis cache.
- There is no separate ledger; `financial_operations` is the operational history and the Kafka consumer builds an audit projection.
- JSON events are used without Schema Registry.
- Authentication and authorization are intentionally out of scope.
- The Outbox Worker is limited to one replica because the fixed schema has no claim/lease state.
- Kafka delivery is at least once, so consumers must remain idempotent.
- Audit data is eventually consistent by design.
- Application JARs must be built on the host before Docker images are built.

These limitations are intentional and match the architecture contract. There are no unfinished mandatory requirements.
