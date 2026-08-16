# Kotlin Coroutines Migration Plan

Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.

This document is an execution plan for a low-cost implementation agent. It migrates the existing Reactor-style Kotlin code to Kotlin coroutines without changing the architecture, public API, database model, consistency guarantees or infrastructure.

Read these documents completely before changing code:

1. [AGENTS.md](AGENTS.md)
2. [ARCHITECTURE.md](ARCHITECTURE.md)
3. [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md)
4. this document

When this plan conflicts with a Reactor-specific implementation detail in the older documents, this plan wins only for the asynchronous programming model. All business, transaction, idempotency, locking, Kafka, Docker and testing guarantees remain mandatory.

## 1. Goal and non-goals

### Goal

Replace application-level `Mono`/`Flux` composition with Kotlin `suspend` functions and sequential Kotlin code while preserving non-blocking WebFlux, Netty and R2DBC execution.

The target is:

- suspending WebFlux controllers;
- suspending application services;
- coroutine-oriented repository ports;
- R2DBC `DatabaseClient` coroutine extensions;
- `TransactionalOperator.executeAndAwait` for explicit reactive transactions;
- a suspending Kafka listener whose completion controls acknowledgement;
- a suspending outbox scheduler that processes records sequentially;
- coroutine-based tests.

### Non-goals

Do not:

- replace WebFlux or Netty;
- replace R2DBC with JDBC, JPA or Hibernate;
- add `CoroutineCrudRepository`; keep the current custom SQL and repository adapters;
- change REST paths, payloads, status codes or OpenAPI contracts;
- change database tables, constraints or Liquibase changesets;
- change Kafka topics, keys, payloads, retry count or DLT behavior;
- change idempotency, row-locking or transaction algorithms;
- add parallel execution inside a database transaction;
- add `GlobalScope`, unmanaged scopes or `runBlocking` to production code;
- use `Dispatchers.IO` for R2DBC calls;
- rewrite unrelated code.

No Liquibase migration is required for this change.

## 2. Required semantic mapping

Use these mappings consistently:

| Current form | Coroutine form |
|---|---|
| `fun command(): Mono<T>` | `suspend fun command(): T` |
| `fun find(): Mono<T>` where absence is valid | `suspend fun find(): T?` |
| `fun write(): Mono<Void>` | `suspend fun write(): Unit` |
| bounded `Flux<T>` query | `suspend fun query(): List<T>` |
| `switchIfEmpty(Mono.error(...))` | nullable result followed by `?: throw ...` |
| `flatMap` / `then` | sequential suspend calls |
| `transactionalOperator.transactional(work)` | `transactionalOperator.executeAndAwait { ... }` |
| `Mono.fromFuture { future }` | `future.await()` |
| `StepVerifier` | `runTest` or `runBlocking` plus assertions |
| concurrent `Flux.flatMap` in integration tests | `coroutineScope { map { async { ... } }.awaitAll() }` |

Use `List` rather than `Flow` for the current history and outbox queries. Both are bounded (`100` and `50` records respectively), and materializing a list keeps this migration smaller and easier to review.

Use these imports rather than inventing custom bridges:

```kotlin
import org.springframework.r2dbc.core.awaitOne
import org.springframework.r2dbc.core.awaitOneOrNull
import org.springframework.r2dbc.core.awaitRowsUpdated
import org.springframework.transaction.reactive.executeAndAwait
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.flow.toList
```

The normal bounded multi-row adapter pattern is:

```kotlin
return databaseClient.sql("...")
    .bind("...", value)
    .map { row, _ -> mapRow(row) }
    .all()
    .asFlow()
    .toList()
```

Use `kotlinx.coroutines.test.runTest` for pure coroutine unit tests. Use `runBlocking` only in integration-test or JUnit lifecycle code that must bridge from a non-suspending test framework method to real external I/O.

## 3. Critical invariants

The migration is not complete unless all these behaviors remain unchanged:

- `SELECT ... FOR UPDATE` and every following write run inside the same R2DBC transaction context;
- transfer account locks are acquired sequentially in deterministic UUID order;
- source and destination balance updates are sequential, not launched with `async`;
- balance, operation, idempotency response and outbox insert commit atomically;
- technical exceptions roll back the complete transaction;
- deterministic business rejections are stored in the idempotency table and commit normally;
- a successful idempotency replay performs no balance or outbox mutation;
- no rejected operation creates a Kafka event;
- the outbox row is marked processed only after Kafka acknowledges the send;
- an outbox send failure increments `attempts` and leaves the row pending;
- the audit listener completes successfully only after the audit database transaction completes;
- audit failures propagate to the existing Kafka retry and DLT handler;
- duplicate audit events remain harmless through `ON CONFLICT (event_id) DO NOTHING`.

Never use `async`, `launch`, `flowOn` or `withContext` inside `executeAndAwait`. The financial transaction must stay sequential and use its transaction-bound connection.

## 4. Phase 0 — establish a baseline

Before editing code:

1. Run `git status --short` and preserve all existing user changes.
2. Run `./gradlew clean build`.
3. Record the current test count and failures, if any.
4. Search production code for `.block()` and verify it is absent.
5. Do not start migration from a failing baseline unless the failure is clearly environmental and documented.

Quality gate:

```bash
./gradlew clean build
rg -n '\.block\(' account-service/src/main financial-operations-service/src/main \
  outbox-worker/src/main audit-consumer/src/main
```

The build must pass and the production search must return no matches.

## 5. Phase 1 — add coroutine dependencies

Modify these Gradle files:

- `account-service/build.gradle.kts`;
- `financial-operations-service/build.gradle.kts`;
- `outbox-worker/build.gradle.kts`;
- `audit-consumer/build.gradle.kts`.

Add to every runnable service:

```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
```

Add to Outbox Worker because `KafkaTemplate.send` returns `CompletableFuture`:

```kotlin
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8")
```

Let the Spring Boot dependency management choose compatible coroutine versions. Do not hard-code a different Kotlin or coroutine version.

At this point do not remove Reactor dependencies. Remove unused direct dependencies only in Phase 7 after source migration and tests pass.

Quality gate:

```bash
./gradlew :account-service:dependencies :financial-operations-service:dependencies
./gradlew :outbox-worker:dependencies :audit-consumer:dependencies
```

The dependency resolution must succeed without version conflicts.

## 6. Phase 2 — migrate Account Service

Migrate Account Service completely before touching Financial Operations Service.

### 6.1 Repository port

Change `AccountRepository` to exactly this style:

```kotlin
interface AccountRepository {
    suspend fun findById(accountId: UUID): Account?
    suspend fun findByIdForUpdate(accountId: UUID): Account?
    suspend fun insert(account: Account)
    suspend fun updateStatus(accountId: UUID, status: AccountStatus, updatedAt: Instant)
    suspend fun tryInsertIdempotency(key: UUID, requestHash: String): Boolean
    suspend fun findIdempotency(key: UUID): AccountIdempotencyRecord?
    suspend fun completeIdempotency(key: UUID, accountId: UUID, responsePayload: String)
}
```

Domain ports must not import Reactor types.

### 6.2 PostgreSQL adapter

In `PostgresAccountRepository`:

- replace `.one()` with `awaitOneOrNull()`;
- replace Boolean `rowsUpdated().map { it == 1L }` with `awaitRowsUpdated() == 1L`;
- execute insert/update statements with `awaitRowsUpdated()`;
- retain all named binds and the internal constant `FOR UPDATE` suffix;
- retain UTC timestamp conversion;
- do not call `.block()`.

Use the Spring R2DBC coroutine extensions from `org.springframework.r2dbc.core`.

### 6.3 Application service

Convert public methods to:

```kotlin
suspend fun create(...): CreationResult
suspend fun get(accountId: UUID): AccountView
suspend fun changeStatus(...): AccountView
```

Use `transactionalOperator.executeAndAwait` around the complete bodies of `create` and `changeStatus`. Keep `get` outside a transaction.

Inside the transaction:

1. call `tryInsertIdempotency`;
2. either replay or create;
3. for status changes, await `findByIdForUpdate` before validation;
4. throw `AccountNotFoundException` for `null`;
5. await each write in order.

Do not move `objectMapper.writeValueAsString` outside the operation where it currently belongs, and preserve all existing exception types.

### 6.4 Controller

Convert controller methods to `suspend fun` returning their actual response type directly. Example:

```kotlin
@GetMapping("/{accountId}")
suspend fun get(@PathVariable accountId: UUID): AccountView = service.get(accountId)
```

For create, await the service and then construct the same `200` replay or `201` first-request response. Do not alter validation annotations or error handling.

### 6.5 Tests

In `AccountPersistenceIntegrationTest`:

- use `runBlocking` in integration-test methods and non-suspending JUnit lifecycle methods;
- make database helpers suspending and use R2DBC await extensions;
- replace concurrent `Flux.flatMap` with `coroutineScope`, `async` and `awaitAll`;
- replace `StepVerifier` error assertions with `assertFailsWith` inside the coroutine;
- keep blocking Liquibase/JDBC setup unchanged because it is test-only migration infrastructure;
- place a timeout around real concurrent database work.

Do not use `runTest` for Testcontainers database integration. Use `runBlocking` because the test waits on real external I/O. Keep `runTest` for pure unit tests.

Quality gate:

```bash
./gradlew :account-service:test
rg -n '\b(Mono|Flux|StepVerifier)\b|\.block\(' account-service/src/main account-service/src/test
```

Expected result: tests pass; no Reactor types or `.block()` remain in Account Service source or tests.

## 7. Phase 3 — migrate Financial Operations Service

This is the highest-risk phase. Preserve the exact transaction order.

### 7.1 Repository port

Convert `FinancialRepository` to:

```kotlin
interface FinancialRepository {
    suspend fun tryInsertIdempotency(key: UUID, hash: String): Boolean
    suspend fun findIdempotency(key: UUID): FinancialIdempotencyRecord?
    suspend fun completeSuccess(key: UUID, operationId: UUID, payload: String)
    suspend fun completeError(key: UUID, payload: String)
    suspend fun lockAccount(accountId: UUID): LockedAccount?
    suspend fun updateBalance(accountId: UUID, balance: BigDecimal, updatedAt: Instant)
    suspend fun insertOperation(operation: FinancialOperation)
    suspend fun insertOutbox(eventId: UUID, operationId: UUID, payload: String, createdAt: Instant)
    suspend fun findOperation(operationId: UUID): FinancialOperation?
    suspend fun findByAccount(accountId: UUID, limit: Int, offset: Long): List<FinancialOperation>
    suspend fun countByAccount(accountId: UUID): Long
}
```

### 7.2 PostgreSQL adapter

Apply the same rules as Account Service:

- single-result selects use `awaitOneOrNull()`;
- required scalar count uses `awaitOne()`;
- DML uses `awaitRowsUpdated()`;
- the bounded multi-row history query is collected into `List<FinancialOperation>`;
- all SQL and bindings remain unchanged;
- nullable UUID binding remains explicit through `bindNull`;
- no blocking bridge is allowed.

### 7.3 Application service

Convert all public use cases to suspending functions:

```kotlin
suspend fun deposit(...): CommandResult
suspend fun withdrawal(...): CommandResult
suspend fun transfer(...): CommandResult
suspend fun get(operationId: UUID): OperationResponse
suspend fun history(...): OperationPage
```

Convert private helpers (`executeSingle`, `persistSuccess`, `acquire`, `reject`) to suspending functions.

Change the `persistSuccess` balance-update argument from `Mono<Void>` to a suspending lambda:

```kotlin
balanceUpdates: suspend () -> Unit
```

Invoke the lambda before inserting operation history, outbox and idempotency success.

Deposit/withdrawal order inside one `executeAndAwait` block:

1. acquire idempotency;
2. return stored result immediately for replay;
3. lock account;
4. validate account and funds;
5. update balance;
6. insert operation;
7. insert outbox event;
8. complete idempotency;
9. return result and commit.

Transfer order inside one `executeAndAwait` block:

1. acquire idempotency;
2. handle replay or same-account rejection;
3. sort both UUIDs exactly as before;
4. await the first lock;
5. await the second lock;
6. map locked accounts back to source and destination;
7. validate both accounts and funds;
8. await source balance update;
9. await destination balance update;
10. insert operation, outbox and idempotency success;
11. return result and commit.

Do not parallelize the two locks or balance updates. Do not catch technical exceptions inside the transaction. They must escape `executeAndAwait` and trigger rollback.

For history, execute the bounded list query and count sequentially. Parallelizing two queries offers no relevant benefit here and complicates context handling.

### 7.4 Controller

Convert all five controller handlers to `suspend fun`. Await the service and use the existing response mapping. Preserve replay status codes and error payloads.

### 7.5 Tests

In `FinancialConsistencyIntegrationTest`:

- replace service `.block()` calls with direct suspend calls inside `runBlocking`;
- replace concurrent Reactor publishers with `async`/`awaitAll`;
- use `withTimeout(30_000)` around concurrency scenarios;
- replace `StepVerifier` rollback assertion with `assertFailsWith<IllegalStateException>`;
- change `FailingOutboxRepository.insertOutbox` to a suspending override that throws the same exception;
- convert R2DBC helpers to suspend functions using await extensions;
- retain all existing assertions and row counts.

The four critical integration tests must still prove:

- concurrent withdrawals never create a negative balance;
- concurrent equal idempotency keys mutate once;
- opposite transfers do not deadlock and preserve total balance;
- outbox failure rolls back balance, operation and idempotency.

Quality gate:

```bash
./gradlew :financial-operations-service:test
rg -n '\b(Mono|Flux|StepVerifier)\b|\.block\(' financial-operations-service/src/main financial-operations-service/src/test
```

Do not proceed until all tests pass and the search returns no matches.

## 8. Phase 4 — migrate Audit Consumer

### 8.1 Repository

Change `AuditRepository.insertIfAbsent` to:

```kotlin
suspend fun insertIfAbsent(event: FinancialOperationCompleted): Boolean
```

Implement it with `awaitRowsUpdated() == 1L`. Preserve `ON CONFLICT (event_id) DO NOTHING` and nullable UUID binding.

### 8.2 Transactional handler

Introduce one explicit testable seam in the application package:

```kotlin
interface AuditEventHandler {
    suspend fun handle(event: FinancialOperationCompleted): Boolean
}
```

Implement `TransactionalAuditEventHandler` as a Spring service. Its `handle` method must call `transactionalOperator.executeAndAwait` and await `repository.insertIfAbsent(event)` inside that transaction. Do not put deserialization or Kafka annotations in this handler.

This split is mandatory. It keeps the Kafka adapter simple and avoids fragile mocking of the `executeAndAwait` extension.

### 8.3 Listener

Convert the listener to a native suspending Kafka listener:

```kotlin
@KafkaListener(...)
suspend fun consume(payload: String)
```

Its sequential behavior must be:

1. deserialize the payload;
2. reject an unsupported `eventType` by throwing;
3. await `AuditEventHandler.handle(event)`;
4. let `TransactionalAuditEventHandler` complete the audit transaction;
5. log inserted or duplicate result;
6. return only after the transaction completes.

Do not catch deserialization, validation or database exceptions. They must propagate so the existing `DefaultErrorHandler` performs three retries and then publishes to `financial-operations.DLT`.

Spring Kafka treats a Kotlin suspending listener as an asynchronous listener and acknowledges only after successful completion. Do not add manual acknowledgement and do not call `subscribe`.

### 8.4 Tests

- use `runTest` for pure consumer tests;
- use a fake suspending `AuditEventHandler` for duplicate and malformed-event tests;
- keep `AuditConfigurationTest` unchanged unless compilation requires import cleanup;
- convert R2DBC `.block()` calls in `KafkaAuditPipelineIntegrationTest` to suspend helpers called through `runBlocking`;
- keep the Kafka client polling code as it is; it is blocking test infrastructure, not production application code;
- rerun duplicate, retry and DLT coverage.

Quality gate:

```bash
./gradlew :audit-consumer:test
rg -n '\b(Mono|Flux|StepVerifier)\b|\.block\(' audit-consumer/src/main audit-consumer/src/test
```

Audit source and tests must contain no Reactor API. Tests may retain Kafka's blocking test client because it is unrelated to Reactor and runs only as test infrastructure.

## 9. Phase 5 — migrate Outbox Worker

### 9.1 Repository

Convert `OutboxRepository` to:

```kotlin
interface OutboxRepository {
    suspend fun findPending(limit: Int): List<OutboxEvent>
    suspend fun markProcessed(eventId: UUID, processedAt: Instant)
    suspend fun incrementAttempts(eventId: UUID)
}
```

Collect the bounded pending query into a list and use R2DBC await extensions for writes.

### 9.2 Publisher

Convert the scheduler to:

```kotlin
@Scheduled(fixedDelayString = "\${outbox.poll-delay-ms:500}")
suspend fun publishPending()
```

Process the batch with a normal `for` loop. Do not use `async`, `launch` or parallel collection. For each event:

1. call `kafkaTemplate.send(topic, key, payload).await()`;
2. after acknowledgement, await `markProcessed`;
3. log only identifiers;
4. on ordinary failure, await `incrementAttempts` and continue to the next record.

Cancellation must not be converted into a publication failure:

```kotlin
catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: Exception) {
    // safe log and increment attempts
}
```

Do not use `runBlocking` or a manually created `CoroutineScope`. Spring supports suspending `@Scheduled` methods through the coroutine-reactor bridge. Fixed delay must remain measured after completion, preserving non-overlapping polling for the single worker instance.

### 9.3 Tests

Convert `OutboxPublisherTest` to `runTest`:

- mock suspend repository methods;
- use completed and exceptionally completed `CompletableFuture` results from KafkaTemplate;
- call `publishPending()` directly;
- verify mark-processed versus increment-attempts exactly as before;
- add one focused test proving a batch is processed in input order if it is not already covered.

Quality gate:

```bash
./gradlew :outbox-worker:test
rg -n '\b(Mono|Flux|StepVerifier)\b|\.block\(' outbox-worker/src/main outbox-worker/src/test
```

Expected result: tests pass and no Reactor API remains in Outbox Worker source or tests.

## 10. Phase 6 — cross-service regression tests

Run the complete test suite before dependency cleanup:

```bash
./gradlew test
```

Specifically verify that these tests were executed, not skipped:

- account concurrent idempotency;
- concurrent withdrawals;
- concurrent opposite-direction transfers;
- financial atomic rollback;
- outbox acknowledged/failure behavior;
- Kafka to audit database pipeline;
- duplicate audit event;
- Kafka retry and DLT behavior;
- shared JSON contract tests.

If a concurrency test fails, do not add sleeps or retries to hide it. Verify that `executeAndAwait` encloses all repository calls and that no work was launched in another coroutine inside the transaction.

## 11. Phase 7 — dependency and source cleanup

After all module tests pass:

1. Remove direct `reactor-kotlin-extensions` dependencies if no source uses them.
2. Remove direct `reactor-test` dependencies if no tests use `StepVerifier` or Reactor test utilities.
3. Keep Reactor transitively through WebFlux and R2DBC; do not exclude it.
4. Remove unused Reactor imports.
5. Do not replace framework internals or force a Reactor-free dependency graph.

Run:

```bash
rg -n '\b(Mono|Flux|StepVerifier)\b|\.block\(|\.subscribe\(' \
  account-service/src financial-operations-service/src outbox-worker/src audit-consumer/src
```

The expected result is no match. The mandatory `AuditEventHandler` seam removes the need to mock Reactor transaction publishers.

Also verify that no blocking persistence stack was added:

```bash
./gradlew :account-service:dependencies :financial-operations-service:dependencies \
  :outbox-worker:dependencies :audit-consumer:dependencies
```

The runtime dependency trees must not contain Spring Data JPA, Hibernate ORM, Spring JDBC or WebMVC.

## 12. Phase 8 — update documentation

Update only documentation statements affected by the programming-model change:

- `AGENTS.md`: state that Kotlin coroutine APIs are preferred over exposed Reactor chains while R2DBC remains reactive;
- `ARCHITECTURE.md`: add an operational note that services use coroutines over WebFlux/R2DBC;
- root `README.md`: describe the backend as coroutine-based and non-blocking;
- service READMEs: mention suspending controllers/services/repositories;
- `IMPLEMENTATION_RESULT.md`: add a migration-result section only after QA succeeds.

Add a link to this plan from `AGENTS.md` and the root README. Do not change OpenAPI files because the HTTP contract is unchanged.

Every modified text/source/configuration file must retain the required copyright note.

## 13. Phase 9 — final QA

Run the complete verification from a clean build:

```bash
./gradlew clean build
docker compose up --build
```

Verify:

- PostgreSQL and Redpanda are healthy;
- `db-migrations` and `redpanda-init` exit successfully;
- all four Spring services are healthy;
- account creation, status changes and reads are unchanged;
- deposit, withdrawal, transfer and history are unchanged;
- idempotent replay still returns the original result;
- concurrent commands preserve balances;
- outbox rows reach `PROCESSED` after Kafka acknowledgement;
- audit rows appear eventually and remain deduplicated;
- retry and DLT behavior remain configured;
- the committed Postman collection passes all happy and error scenarios;
- Swagger/OpenAPI remains available.

Stop Compose without deleting persisted user data unless a clean test database was explicitly intended.

## 14. Definition of Done

The migration is complete only when every item is true:

- [ ] WebFlux, Netty and R2DBC remain in use.
- [ ] No JPA, Hibernate, JDBC runtime access or WebMVC was introduced.
- [ ] Controller and application-service APIs use Kotlin `suspend` functions.
- [ ] Repository ports expose no Reactor types.
- [ ] Database adapters use non-blocking R2DBC coroutine awaits.
- [ ] Account and financial command transactions use `executeAndAwait`.
- [ ] Both transfer locks are acquired sequentially in deterministic order in one transaction.
- [ ] Atomic rollback, negative-balance and concurrency tests pass.
- [ ] Kafka publication still occurs only through the transactional outbox.
- [ ] The outbox scheduler is suspending, sequential and cancellation-safe.
- [ ] The Kafka listener is suspending and acknowledges only after audit persistence succeeds.
- [ ] Duplicate audit events, retries and DLT behavior pass tests.
- [ ] No `.block()`, manual `subscribe`, `GlobalScope` or production `runBlocking` exists.
- [ ] No production `Mono` or `Flux` imports remain in the four services.
- [ ] Unit and integration tests pass without being disabled.
- [ ] `./gradlew clean build` succeeds.
- [ ] Docker Compose starts the complete system successfully.
- [ ] Postman scenarios pass without contract changes.
- [ ] Documentation reflects the coroutine programming model.

## 15. Failure policy for the implementation agent

When a phase fails:

1. stop that phase;
2. identify the first real compilation or test failure;
3. fix the cause without weakening assertions or removing tests;
4. rerun the smallest failing test;
5. rerun the complete module quality gate;
6. continue only after the module is green.

Do not revert to `.block()`, wrap production code in `runBlocking`, remove transactions, relax locking or convert integration tests into mocks to make the build pass. Do not change architecture unless there is a documented technical blocker.

## 16. Framework references

Use these official references if an API signature is unclear:

- [Spring Framework — Kotlin coroutines](https://docs.spring.io/spring-framework/reference/languages/kotlin/coroutines.html)
- [Spring R2DBC coroutine extensions](https://docs.spring.io/spring-framework/docs/current/kdoc-api/spring-r2dbc/org.springframework.r2dbc.core/index.html)
- [Spring Kafka — asynchronous listener return types](https://docs.spring.io/spring-kafka/reference/kafka/receiving-messages/async-returns.html)
- [Spring Framework — reactive and suspending scheduled methods](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)
