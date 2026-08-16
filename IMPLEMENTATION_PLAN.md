# Implementation Plan

Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.

This is the execution plan for a low-cost implementation agent. Follow [AGENTS.md](AGENTS.md) as the mandatory engineering contract and use [ARCHITECTURE.md](ARCHITECTURE.md) for the target model, API examples and documented trade-offs. Do not introduce functionality or infrastructure outside those documents.

## 1. Locked decisions

These decisions are final for this implementation:

- Language: Kotlin.
- Runtime: Java 25.
- Build: Gradle Kotlin DSL multi-project build.
- Web stack: Spring Boot, Spring WebFlux and Netty.
- Runtime database access: Spring Data R2DBC/PostgreSQL R2DBC; no JPA or blocking JDBC in application services.
- Database: one shared PostgreSQL instance.
- Migrations: one central Liquibase `db-migrations` container using JDBC only for migrations.
- Messaging: Redpanda with the Kafka protocol.
- Event format: JSON without Schema Registry.
- Consistency: strong consistency for balances, operation history and outbox; eventual consistency only for audit records.
- Concurrency: PostgreSQL pessimistic row locks using `SELECT ... FOR UPDATE` executed through R2DBC `DatabaseClient`.
- Idempotency: persisted `Idempotency-Key` plus request hash and stored response.
- Kafka delivery: at least once; consumer deduplicates by `event_id`.
- Security: authentication and authorization are out of scope.
- Caching, Redis, CQRS, a separate ledger, database-per-service and mobile BFF are out of scope.
- Money: Kotlin `BigDecimal` and PostgreSQL `NUMERIC(19,4)` only.
- Time: UTC internally and ISO-8601 UTC in APIs/events.
- Every created or modified text/source/configuration file must contain the copyright notice required by `AGENTS.md` where its format permits comments or metadata.

Do not revisit these choices during implementation unless the build is technically impossible. If that happens, report the exact blocker before changing architecture.

## 2. Target repository layout

Create one Gradle multi-project repository:

```text
.
├── account-service/
│   ├── Dockerfile
│   ├── README.md
│   ├── openapi.yaml
│   ├── build.gradle.kts
│   └── src/
├── financial-operations-service/
│   ├── Dockerfile
│   ├── README.md
│   ├── openapi.yaml
│   ├── build.gradle.kts
│   └── src/
├── outbox-worker/
│   ├── Dockerfile
│   ├── README.md
│   ├── openapi.yaml
│   ├── build.gradle.kts
│   └── src/
├── audit-consumer/
│   ├── Dockerfile
│   ├── README.md
│   ├── openapi.yaml
│   ├── build.gradle.kts
│   └── src/
├── shared-contracts/
│   ├── build.gradle.kts
│   └── src/main/kotlin/
├── infrastructure/
│   └── db-migrations/
│       ├── Dockerfile
│       └── changelog/
├── postman/
│   ├── bank-system.postman_collection.json
│   └── local.postman_environment.json
├── docs/
│   └── bank_4.drawio.svg
├── docker-compose.yml
├── settings.gradle.kts
├── build.gradle.kts
├── AGENTS.md
├── ARCHITECTURE.md
├── IMPLEMENTATION_PLAN.md
└── README.md
```

`shared-contracts` contains only stable cross-service event DTOs and serialization configuration. Do not put repositories, database entities or business services there.

Use the same package convention in each Spring service:

```text
com.adampuchala.bank.<service>.adapter.`in`
com.adampuchala.bank.<service>.application
com.adampuchala.bank.<service>.domain
com.adampuchala.bank.<service>.adapter.out
com.adampuchala.bank.<service>.configuration
```

## 3. Version and build baseline

### Tasks

- Upgrade the Gradle wrapper from `9.0.0` to `9.1.0` or newer, because Gradle `9.0.0` cannot run on Java 25.
- Change the root Kotlin toolchain from Java 21 to Java 25.
- Pin Spring Boot `4.1.0`, which explicitly supports Java 25 and Gradle 9.x.
- Keep the existing Kotlin plugin `2.3.0` unless Spring Initializr reports a compatibility conflict; do not introduce a prerelease Kotlin version.
- Keep Kotlin/JVM target and Java toolchain aligned at 25.
- Apply consistent Kotlin compiler settings and strict null-safety for Spring annotations.
- Configure deterministic JAR names:
  - `account-service.jar`;
  - `financial-operations-service.jar`;
  - `outbox-worker.jar`;
  - `audit-consumer.jar`.
- Set each Spring module's `bootJar.archiveFileName` explicitly to the name above and disable the plain JAR where it is not needed.
- Add a root `buildAll` task or document `./gradlew clean build` as the host build command.

### Required dependencies by service

Account and Financial Operations services:

- Spring WebFlux;
- Spring Validation;
- Spring Data R2DBC;
- PostgreSQL R2DBC driver;
- Spring Actuator;
- Springdoc WebFlux UI;
- Jackson Kotlin;
- Kotlin reflection;
- Reactor Kotlin extensions;
- JUnit 5, Mockito Kotlin, Reactor Test and Testcontainers.

Outbox Worker:

- Spring WebFlux and Netty for Actuator health;
- Spring Data R2DBC;
- PostgreSQL R2DBC driver;
- Spring Kafka;
- Jackson Kotlin;
- Spring Actuator;
- testing dependencies.

Audit Consumer:

- Spring WebFlux and Netty for Actuator health;
- Spring Data R2DBC;
- PostgreSQL R2DBC driver;
- Spring Kafka;
- Jackson Kotlin;
- Spring Actuator;
- testing dependencies.

Do not add Hibernate, JPA, Redis, Spring Security, Schema Registry clients, WebMVC or servlet containers.

### Quality gate

- `./gradlew projects` lists all modules.
- `./gradlew clean build` succeeds on Java 25 before Docker work begins.
- Every expected JAR exists in its module's `build/libs` directory.

## 4. Database migrations

Create a central Liquibase changelog with immutable, ordered changesets.

### Changeset 001: types and `user_accounts`

Create `user_accounts`:

- `account_id UUID PRIMARY KEY`;
- `first_name VARCHAR(100) NOT NULL`;
- `last_name VARCHAR(100) NOT NULL`;
- `balance NUMERIC(19,4) NOT NULL DEFAULT 0`;
- `status VARCHAR(20) NOT NULL`;
- `version BIGINT NOT NULL DEFAULT 0`;
- `created_at TIMESTAMPTZ NOT NULL`;
- `updated_at TIMESTAMPTZ NOT NULL`;
- check `balance >= 0`;
- check `status IN ('ACTIVE','BLOCKED','CLOSED')`.

Create `create_account_idempotency_store`:

- `idempotency_key UUID PRIMARY KEY`;
- `account_id UUID NULL`;
- `request_hash VARCHAR(64) NOT NULL`;
- `response_payload JSONB NULL`;
- `status VARCHAR(20) NOT NULL`;
- `created_at TIMESTAMPTZ NOT NULL`;
- check `status IN ('PENDING','SUCCESS','ERROR')`;
- foreign key from `account_id` to `user_accounts`.

### Changeset 002: financial operations

Create `financial_operations`:

- `operation_id UUID PRIMARY KEY`;
- `type VARCHAR(20) NOT NULL`;
- `from_account_id UUID NULL`;
- `to_account_id UUID NULL`;
- `amount NUMERIC(19,4) NOT NULL`;
- `status VARCHAR(20) NOT NULL`;
- `description VARCHAR(500) NULL`;
- `created_at TIMESTAMPTZ NOT NULL`;
- check `amount > 0`;
- check `type IN ('DEPOSIT','WITHDRAWAL','TRANSFER')`;
- check `status IN ('SUCCESS','REJECTED')`;
- check account columns match the operation type:
  - deposit: `from_account_id IS NULL`, `to_account_id IS NOT NULL`;
  - withdrawal: `from_account_id IS NOT NULL`, `to_account_id IS NULL`;
  - transfer: both account IDs are non-null and different;
- foreign keys from both account columns to `user_accounts`.

Add indexes on `(from_account_id, created_at DESC)`, `(to_account_id, created_at DESC)` and `created_at`.

Create `financial_operations_idempotency_store`:

- `idempotency_key UUID PRIMARY KEY`;
- `operation_id UUID NULL`;
- `request_hash VARCHAR(64) NOT NULL`;
- `response_payload JSONB NULL`;
- `status VARCHAR(20) NOT NULL`;
- `created_at TIMESTAMPTZ NOT NULL`;
- check `status IN ('PENDING','SUCCESS','ERROR')`;
- foreign key from `operation_id` to `financial_operations`.

### Changeset 003: outbox

Create `financial_operation_result_outbox`:

- `event_id UUID PRIMARY KEY`;
- `operation_id UUID NOT NULL UNIQUE`;
- `payload JSONB NOT NULL`;
- `status VARCHAR(20) NOT NULL DEFAULT 'PENDING'`;
- `attempts INTEGER NOT NULL DEFAULT 0`;
- `created_at TIMESTAMPTZ NOT NULL`;
- `processed_at TIMESTAMPTZ NULL`;
- check `status IN ('PENDING','PROCESSED')`;
- check `attempts >= 0`;
- foreign key from `operation_id` to `financial_operations`.

Add an index on `(status, created_at)` for polling.

### Changeset 004: audit

Create `financial_operations_audit_events`:

- `event_id UUID PRIMARY KEY`;
- `operation_id UUID NOT NULL`;
- `from_account_id UUID NULL`;
- `to_account_id UUID NULL`;
- `amount NUMERIC(19,4) NOT NULL`;
- `created_at TIMESTAMPTZ NOT NULL`;
- check `amount > 0`.

Do not add a foreign key from audit records to operational tables. The audit consumer is an asynchronous projection and must be able to preserve an event independently.

### Migration container

- Pin `infrastructure/db-migrations/Dockerfile` to `liquibase/liquibase:4.33.0`, which predates the Liquibase 5 modular-driver change, and verify that the PostgreSQL JDBC driver is present in the image.
- If the pinned image does not contain the driver, add one pinned PostgreSQL JDBC JAR under `/liquibase/lib` in the custom migration image; do not depend on an unversioned runtime download.
- Copy changelogs into the image; do not generate them at runtime.
- Use PostgreSQL JDBC only inside this container.
- Configure URL, username and password through environment variables.
- Add a PostgreSQL health check.
- Configure `db-migrations` to wait for healthy PostgreSQL and exit successfully after `liquibase update`.
- Configure application services to start only after `db-migrations` completes successfully.
- In integration tests, start PostgreSQL with Testcontainers and apply the same central changelog through test-scoped Liquibase/JDBC before creating the R2DBC application context. Do not duplicate the schema in test SQL.

### Quality gate

- A new empty database is migrated successfully.
- A second Liquibase run makes no changes and succeeds.
- All constraints and indexes exist.
- No Spring application attempts to run Liquibase or Hibernate DDL.

## 5. Account Service

### API

Implement exactly:

- `POST /api/v1/accounts` with required `Idempotency-Key` UUID header;
- `GET /api/v1/accounts/{accountId}`;
- `PUT /api/v1/accounts/{accountId}/status`.

Use the request/response/error shapes from `ARCHITECTURE.md`. Validate names as non-blank with a reasonable maximum length. Do not accept an initial balance during account creation.

### Business rules

- New accounts are `ACTIVE` with balance `0.0000`.
- `ACTIVE -> BLOCKED`, `BLOCKED -> ACTIVE`, `ACTIVE -> CLOSED` and `BLOCKED -> CLOSED` are allowed.
- Closing is allowed only when balance is exactly zero.
- `CLOSED` is terminal.
- Repeating the current non-terminal status may be treated as an idempotent success.
- Status changes must lock the account row before checking balance/status so they serialize with financial operations.

### Idempotent account creation

In one R2DBC transaction:

1. Canonicalize and hash the semantic request (`firstName`, `lastName`) with SHA-256.
2. Insert `PENDING` idempotency row with the unique header key.
3. If the key already exists, read it:
   - different hash -> `409 IDEMPOTENCY_KEY_REUSED`;
   - `SUCCESS` -> deserialize and return the stored response;
   - `ERROR` -> return the stored deterministic error;
   - `PENDING` after the conflicting insert has waited -> reread once and return the completed result; do not spin.
4. Create the account.
5. Store `account_id`, serialized response and `SUCCESS` in the idempotency row.
6. Commit, then return `201` for the first execution. A successful replay returns `200` with the same response body. This avoids adding an HTTP-status column to the fixed idempotency model and must be documented in OpenAPI.

Unexpected technical failures roll back the transaction. Do not cache a `500` response.

### Persistence

- Use repository ports in the application/domain layer.
- Implement row locking with custom SQL through `DatabaseClient`; Spring Data R2DBC has no JPA-style `@Lock`.
- Configure an explicit `R2dbcTransactionManager` and `TransactionalOperator` in Account Service and Financial Operations Service. Use the operator around the complete reactive command pipeline so the transaction boundary is visible and testable.
- Use PostgreSQL `READ COMMITTED` isolation. Row locks, uniqueness constraints and check constraints provide the required guarantees; do not silently raise global isolation.
- Never call `block()` in production code.
- Add an integration test that injects a failure before commit and proves that account/idempotency or balance/operation/outbox changes all roll back together.

### Tests

Unit:

- `shouldCreateActiveAccountWithZeroBalance`;
- `shouldRejectClosingAccountWithNonZeroBalance`;
- `shouldRejectTransitionFromClosedAccount`;
- `shouldReturnStoredAccountForRepeatedIdempotencyKey`;
- `shouldRejectSameIdempotencyKeyWithDifferentRequest`.

Integration with PostgreSQL Testcontainer:

- happy-path create/get/status change;
- repeated concurrent create request creates one account;
- status change serializes correctly with a financial row lock.

### Deliverables

- Kotlin source and tests;
- `openapi.yaml` matching implementation;
- service `README.md`;
- runtime-only `Dockerfile` copying `build/libs/account-service.jar`;
- Actuator health endpoint.

## 6. Financial Operations Service

### API

Implement exactly:

- `POST /api/v1/accounts/{accountId}/deposits`;
- `POST /api/v1/accounts/{accountId}/withdrawals`;
- `POST /api/v1/transfers`;
- `GET /api/v1/accounts/{accountId}/operations?page=0&size=20`;
- `GET /api/v1/operations/{operationId}`.

Require a UUID `Idempotency-Key` header for all three command endpoints. History endpoints do not require it. Cap history page size at 100 and sort newest first.

### Command validation

- Amount must be positive and have no more than four fractional digits.
- Description is optional and limited to 500 characters.
- Transfer source and destination must differ.
- Every involved account must exist and be `ACTIVE`.
- Withdrawals and transfers must not produce a negative source balance.
- Deposits are also rejected for `BLOCKED` and `CLOSED` accounts, matching `ARCHITECTURE.md`.

### Locking and transaction algorithm

Use one reactive PostgreSQL transaction per command.

Deposit/withdrawal:

1. Acquire/create the idempotency record.
2. Lock the account with `SELECT ... FOR UPDATE`.
3. Apply guard clauses for existence, status and amount/funds.
4. Update balance and increment `version`.
5. Insert one `financial_operations` row with `SUCCESS`.
6. Create one `FinancialOperationCompleted` event.
7. Insert the serialized event into outbox as `PENDING`.
8. Store the response in idempotency storage as `SUCCESS`.
9. Commit.

Transfer:

1. Acquire/create the idempotency record.
2. Sort the two UUIDs lexicographically by their canonical string or UUID value.
3. Lock the first account, then the second with `SELECT ... FOR UPDATE`.
4. Map locked rows back to source and destination.
5. Validate both statuses and source balance.
6. Debit source and credit destination.
7. Insert one `TRANSFER` operation.
8. Insert one outbox event for that operation.
9. Store the response and commit.

Do not call Account Service over HTTP for validation. The documented shared-database exception exists specifically so status, balance, operation and outbox are atomic.

Use explicit PostgreSQL locking SQL through `DatabaseClient`:

```sql
SELECT account_id, balance, status, version
FROM user_accounts
WHERE account_id = :accountId
FOR UPDATE
```

For a transfer, execute this query twice on the same transaction-bound R2DBC connection, using the deterministically sorted IDs. Map the resulting rows back to source/destination before applying business rules.

### Rejected commands

Use this fixed behavior:

- Bean/input validation failures return `400` and are not persisted.
- Missing accounts return `404` and are not persisted as financial operations.
- Deterministic business rejections (`ACCOUNT_BLOCKED`, `ACCOUNT_CLOSED`, `INSUFFICIENT_FUNDS`, same transfer account) return `409`.
- Store the deterministic error response in the idempotency table so an identical retry returns the same response.
- Do not insert a `financial_operations` row and do not insert an outbox event for rejected commands. The allowed `REJECTED` status remains reserved for future audit expansion and is not used in this MVP.
- Technical failures roll back and are not persisted as idempotency results.

When storing a business rejection, complete the transaction normally with an application result object; map that result to HTTP after commit. Do not throw an exception that causes the idempotency row to roll back.

### Idempotency

Use the same conflict algorithm as Account Service. The request hash must include:

- operation type;
- path account ID where applicable;
- source/destination IDs;
- normalized decimal amount;
- description.

A successful replay returns `200` with the stored response without reacquiring account locks or mutating balances. A stored deterministic business rejection is mapped back to its original `409` using the stored error `code`. No additional HTTP-status column is required.

### History

- Query `financial_operations` directly; do not query Kafka or audit tables.
- An account participates in a record when it matches either `from_account_id` or `to_account_id`.
- Use a deterministic secondary sort by `operation_id` after `created_at DESC`.
- Return the paged envelope from `ARCHITECTURE.md`.
- Return one operation DTO from `GET /operations/{operationId}` rather than the paged envelope.

### Tests

Unit:

- success cases for each command;
- insufficient funds;
- blocked/closed account;
- same-account transfer;
- idempotency replay and hash conflict.

Integration with PostgreSQL Testcontainer:

- deposit, withdrawal and transfer update balances and history atomically;
- concurrent withdrawals cannot make balance negative;
- opposite-direction concurrent transfers complete without deadlock and preserve total balance;
- repeated concurrent idempotency key mutates balance once and creates one operation/outbox row;
- failed command creates no outbox event;
- history includes incoming and outgoing transfers and is newest first.

### Deliverables

- Kotlin source and tests;
- `openapi.yaml` matching implementation;
- service `README.md`;
- runtime-only `Dockerfile` copying `build/libs/financial-operations-service.jar`;
- Actuator health endpoint.

## 7. Shared Kafka contract

In `shared-contracts`, define one immutable Kotlin DTO for `FinancialOperationCompleted`. Preserve the JSON names from `ARCHITECTURE.md`:

- `eventId: UUID`;
- `eventType: String` fixed to `FINANCIAL_OPERATION_COMPLETED`;
- `operationId: UUID`;
- `operationType: DEPOSIT | WITHDRAWAL | TRANSFER`;
- `fromAccountId: UUID?`;
- `toAccountId: UUID?`;
- `amount: BigDecimal`;
- `occurredAt: Instant`.

Use Jackson JSON with explicit configuration for Kotlin, `Instant`, UUID and `BigDecimal`. Add serialization contract tests using a fixed fixture. Do not include account-holder names, balances, idempotency keys or other sensitive/unnecessary data in the event.

## 8. Outbox Worker

### Behavior

- Run as a separate Spring Boot WebFlux/Netty service without a business REST API.
- Poll `financial_operation_result_outbox` at a configurable interval, default 500 ms.
- Use a non-overlapping fixed-delay schedule; a new poll must not start until the previous batch finishes.
- Read a small batch, default 50, ordered by `created_at`.
- For this single-worker MVP, process records sequentially to keep behavior obvious.
- Publish payload unchanged to topic `financial-operations` with `operation_id` as key.
- Wait reactively for the Kafka send result; never call `block()` on a Netty thread.
- After broker acknowledgement, update that row to `PROCESSED` and set `processed_at`.
- On failure, increment `attempts`, retain `PENDING`, log identifiers and retry on the next poll.
- A crash after Kafka acknowledgement and before the database update may publish a duplicate. This is expected and handled by the consumer.

Do not wrap PostgreSQL and Kafka in a distributed transaction. Do not mark an event processed before Kafka acknowledges it.

### Concurrency

Run exactly one worker replica. The fixed schema has no `CLAIMED` state or lease, so `SKIP LOCKED` in a short transaction would not safely coordinate multiple replicas after the lock is released. Read pending rows in order, publish sequentially and document the single-replica constraint. Do not add worker replicas without first extending the schema with an explicit claim/lease model.

### Tests

- publishes a pending event with the operation ID key;
- marks acknowledged event as processed;
- increments attempts and leaves pending after failure;
- publishing the same outbox event twice remains safe for the consumer.

### Deliverables

- Kotlin source and tests;
- minimal `openapi.yaml` documenting only exposed Actuator health if required by the project convention;
- service `README.md` describing the single-replica constraint and duplicate window;
- runtime-only `Dockerfile` copying `build/libs/outbox-worker.jar`;
- Actuator health endpoint.

## 9. Audit Consumer

### Behavior

- Consume topic `financial-operations` using the stable consumer group `financial-operations-audit`.
- Deserialize the explicit JSON contract.
- Reject malformed/unsupported events into the retry/DLT flow.
- Insert audit data using `INSERT ... ON CONFLICT (event_id) DO NOTHING` in a reactive database transaction.
- Treat zero inserted rows as an already-processed duplicate and complete successfully.
- Do not call the operational services and do not modify balances/history/outbox.

### Retry and reprocessing

- Use a `@KafkaListener` method returning `Mono<Void>`. Spring Kafka's supported asynchronous return handling must defer acknowledgement until the returned `Mono` completes. Propagate reactive database errors through the `Mono`; never subscribe manually inside the listener.
- Configure a `DefaultErrorHandler` with `DeadLetterPublishingRecoverer` and `FixedBackOff(1000, 3)`: three retries at one-second intervals, then DLT.
- Keep the listener container's async-return acknowledgement behavior; do not manually acknowledge before the `Mono` completes.
- After retries are exhausted, publish to `financial-operations.DLT`.
- Keep the original key and payload on the DLT.
- Document a manual reprocessing command using Redpanda `rpk`: inspect a DLT message and republish the unchanged key/payload to `financial-operations` after the cause is fixed.
- Do not automatically loop DLT messages back to the main topic.

The Kafka listener may use Spring Kafka's listener threads. Database work remains R2DBC and must be subscribed/awaited correctly before acknowledging the record. Do not acknowledge the Kafka record before the audit transaction completes.

### Tests

- successful event creates one audit row;
- duplicate event creates no second row and is acknowledged;
- transient failure is retried;
- exhausted failure reaches DLT;
- reprocessed valid event remains idempotent.

### Deliverables

- Kotlin source and tests;
- minimal `openapi.yaml` documenting only exposed Actuator health if required by the project convention;
- service `README.md` with retry/DLT/reprocessing procedure;
- runtime-only `Dockerfile` copying `build/libs/audit-consumer.jar`;
- Actuator health endpoint.

## 10. Docker Compose

Define these services:

- `postgres`;
- `db-migrations`;
- `redpanda`;
- `redpanda-init`;
- `account-service`;
- `financial-operations-service`;
- `outbox-worker`;
- `audit-consumer`.

Optional only if it costs little: Redpanda Console for observing topics. It is not part of the business architecture.

`redpanda-init` is a one-shot infrastructure service using `rpk`. After Redpanda is healthy, it creates `financial-operations` and `financial-operations.DLT` with three partitions and replication factor one. Topic creation must be idempotent. Application services wait for this step to finish successfully.

### Startup order

```text
postgres healthy -> db-migrations completed successfully
redpanda healthy -> redpanda-init completed -----┤
db-migrations completed -> application services ├-> running system
                                                 ┘
```

### Ports

Use stable defaults and document them:

- PostgreSQL: host `5432`;
- Redpanda Kafka: host `19092`, container `9092`;
- Account Service: `8081`;
- Financial Operations Service: `8082`;
- Outbox Worker health: `8083`;
- Audit Consumer health: `8084`.

### Configuration

- Configure all credentials, R2DBC URLs, Kafka brokers, topic names, consumer groups and polling intervals through environment variables.
- Commit only safe local development defaults.
- Add health checks for PostgreSQL, Redpanda and HTTP services.
- Add restart policies only where they do not hide startup failures.
- Do not add Redis, ZooKeeper, Schema Registry or an API gateway/BFF.

### JAR/image build rule

Every application Dockerfile must only copy and run its already-built JAR; no Gradle command may appear in a Dockerfile. Therefore the reproducible command sequence from a clean checkout is:

```bash
./gradlew clean build
docker compose up --build
```

Do not claim that `docker compose up --build` alone compiles application JARs. It only builds runtime images from existing JARs. Document this explicit trade-off in the root README so the Docker rule is truthful and reproducible.

### Quality gate

- The two-command clean startup succeeds.
- All four Spring services report healthy.
- No application starts before migrations finish.
- Kafka topic and DLT exist or are created deterministically.

## 11. Error handling and observability

Create one consistent API error model:

- `code`;
- `message`;
- `timestamp`;
- `path`.

Use centralized WebFlux exception handling. Required error codes include:

- `VALIDATION_ERROR`;
- `ACCOUNT_NOT_FOUND`;
- `OPERATION_NOT_FOUND`;
- `ACCOUNT_BLOCKED`;
- `ACCOUNT_CLOSED`;
- `INSUFFICIENT_FUNDS`;
- `INVALID_STATUS_TRANSITION`;
- `IDEMPOTENCY_KEY_REQUIRED`;
- `IDEMPOTENCY_KEY_REUSED`;
- `INTERNAL_ERROR`.

Logs must be structured and include only safe correlation fields such as `accountId` when necessary, `operationId`, `eventId` and `idempotencyKey`. Never log account-holder names, complete request/response payloads, database credentials, tokens or other sensitive information. Do not log stack traces for expected business rejections.

## 12. OpenAPI and Postman

### OpenAPI

- Account Service documents all account endpoints, headers, validation and error responses.
- Financial Operations Service documents command/history endpoints, pagination, idempotency and error responses.
- Worker/consumer OpenAPI files state that no business API is exposed and document the health endpoint if included.
- Keep static `openapi.yaml` files consistent with Spring controllers. Swagger UI must be active for the two API services.
- Add an OpenAPI validation task or contract test that fails the build when either API service's static specification is invalid or its documented paths diverge from the implemented controller paths.

### Postman

Create one collection with variables for both service base URLs and generated account IDs. Include runnable scenarios:

1. create source account;
2. create destination account;
3. deposit into source;
4. transfer source to destination;
5. withdraw from destination;
6. retrieve both account details;
7. retrieve account history;
8. retrieve operation by ID;
9. replay a deposit with the same idempotency key and prove no second mutation;
10. attempt insufficient-funds withdrawal and assert `409`;
11. block an account and assert an operation is rejected;
12. close a zero-balance account and assert it cannot be reopened.

Use Postman scripts only for capturing IDs and assertions. Do not embed secrets.

## 13. Final QA session

Run this phase after implementation, not incrementally in place of service tests.

### Build and static checks

- Run `./gradlew clean build` on Java 25.
- Confirm no JPA/Hibernate/WebMVC/Redis/Schema Registry dependencies.
- Search production code for `.block(` and remove every request-path occurrence.
- Confirm every model-changing commit has a Liquibase changeset.
- Confirm copyright notes are present where supported.

### Integration checks

- Start from an empty database with the documented two-command flow.
- Run the Postman collection.
- Verify balances and history after deposit, withdrawal and transfer.
- Run concurrent withdrawal/transfer tests and verify no negative balance/deadlock.
- Replay idempotent requests concurrently and verify exactly one mutation, one operation and one outbox event.
- Stop Redpanda, complete a financial operation, restart Redpanda and verify the pending outbox event is eventually published.
- Deliver the same Kafka event twice and verify one audit row.
- Trigger a consumer failure, verify retries and DLT, then follow the documented reprocessing procedure.
- Verify operation history is available before the audit record appears.

### Documentation checks

- Root README contains prerequisites, build/start/stop commands, service URLs, Swagger URLs, Postman instructions, architecture link and trade-offs.
- Each service README contains purpose, configuration and local/test instructions.
- OpenAPI files match the implemented endpoints and HTTP status codes.
- The architecture diagram link works from the repository.

## 14. Delivery definition of done

The implementation is complete only when:

- all mandatory API features work;
- negative balances are impossible under concurrency;
- repeated requests are idempotent, including concurrent repeats;
- successful financial operations atomically produce history and outbox records;
- the outbox worker publishes to Redpanda after commit;
- the consumer writes one audit row per `event_id` and supports retry/DLT/reprocessing;
- the clean build and Docker Compose startup flow works;
- Swagger/OpenAPI, Postman and READMEs are present and accurate;
- the focused unit/integration test suite passes;
- no optional BFF/mobile/CQRS/cache/security work has displaced mandatory scope.

## 15. Execution discipline for a low-cost agent

Work one numbered phase at a time. After each phase:

1. run only the smallest relevant tests/build task;
2. fix failures before continuing;
3. report files changed and the quality gate result;
4. do not refactor completed phases without a failing test or explicit requirement;
5. do not add dependencies or architecture patterns not named in this plan.

If time becomes constrained, stop after completing the current consistency boundary. Prioritize in this order:

1. account and financial APIs;
2. database transactions, locking and idempotency;
3. operation history and outbox;
4. Kafka consumer deduplication and retry;
5. Docker/OpenAPI/Postman/README;
6. optional polish.
