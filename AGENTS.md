# Implementation Guide for Agents

This document is the implementation-level companion to [ARCHITECTURE.md](ARCHITECTURE.md). The architecture document describes the target design, API examples and trade-offs. This file defines the expected engineering approach and delivery rules. Agents implementing the system must execute [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) phase by phase.

The completed coroutine migration is specified in [COROUTINES_MIGRATION_PLAN.md](COROUTINES_MIGRATION_PLAN.md). When implementing or reviewing that migration, it overrides only Reactor-specific programming-model details from the older plan; all architecture and consistency requirements remain binding.

## Copyright notice

Every source, configuration, documentation and generated project file created or modified for this exercise must include the following note where the file format supports comments or metadata:

```text
Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
```

Use the correct comment syntax for the file type (`//`, `#`, `<!-- -->`, `/* */`, or an equivalent metadata field). Do not put executable copyright text into JSON, YAML or other formats that do not support comments; use the appropriate documented metadata field or a neighboring documentation note instead. The notice applies only to this recruitment exercise and does not claim ownership of third-party dependencies or generated framework files that cannot be modified safely.

## Primary implementation goal

Implement a small, production-minded banking backend that is reliable under repeated requests and concurrent financial operations. Prefer a complete, understandable solution over broad but unfinished functionality.

The required backend features are:

- create, retrieve and update account status;
- deposit, withdrawal and transfer;
- no negative balances;
- synchronous operation history;
- idempotent account creation and financial operations;
- transactional outbox;
- Kafka publication after successful financial operations;
- idempotent audit consumer with retry/reprocessing support;
- Swagger/OpenAPI documentation;
- Docker Compose startup;
- Postman collection and README documentation.

The mobile BFF and native mobile application are optional and should not delay completion of the backend.

## Technology and infrastructure

Use the following technologies unless a documented repository constraint requires otherwise:

- Kotlin and Spring Boot services using Spring WebFlux and Netty;
- Kotlin coroutines (`suspend`, `runTest`) as the application programming model over WebFlux;
- Java 25;
- reactive PostgreSQL access using Spring Data R2DBC and the PostgreSQL R2DBC driver;
- Liquibase for database schema migrations;
- PostgreSQL as the database;
- Redpanda as the Kafka-compatible broker in Docker Compose;
- Docker Compose for the complete local environment;
- Testcontainers for integration tests;
- JUnit 5 and Mockito for unit tests;
- Springdoc/OpenAPI for generated API documentation.

Do not use blocking JDBC/JPA access in request-handling paths. With WebFlux and Netty, database access must use R2DBC. If a blocking library is unavoidable in infrastructure code, isolate it explicitly and do not execute it on the Netty event-loop threads.

Application and domain ports should not expose Reactor `Mono` or `Flux`; use suspending functions and bounded `List` results. Reactor remains a transitive framework implementation detail and may be used only where a Spring integration adapter explicitly requires it.

The repository provides access to the terminal `spring init` command. Use it to generate Spring Boot service skeletons where useful rather than manually creating inconsistent project structures.

Use Kotlin consistently for application code, tests and service configuration where Kotlin configuration is supported.

Liquibase must run automatically as a dedicated `db-migrations` service in Docker Compose before application services start. Because the project uses one shared PostgreSQL database, do not run Liquibase independently from every application. Every database schema change must be represented by a new Liquibase changeset. Do not modify an already-applied changeset and do not rely on Hibernate auto-DDL to change the schema. When the domain model changes, update the Kotlin model and add a corresponding Liquibase changeset in the same change.

The migration service may use the Liquibase CLI or a small dedicated migration image with the PostgreSQL JDBC driver. Application services use R2DBC for runtime database access; the migration container is infrastructure code and may use JDBC because Liquibase requires it. Docker Compose should make application services depend on successful migration completion where supported.

Every service must include:

- its own `openapi.yaml` describing the exposed API;
- a short service-level `README.md` with purpose, local run instructions and configuration;
- a Dockerfile or an explicit Docker Compose build configuration;
- health/readiness configuration where practical.

Every runnable service must have its own `Dockerfile`. Dockerfiles are runtime packaging files, not build environments: the application JAR must be built before the Docker image build and copied into the image. Do not run Gradle or build the JAR inside the Dockerfile.

Expected pattern:

```dockerfile
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY build/libs/service.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

The exact JAR name may differ per service, but the image must run the already-built artifact. Docker Compose should build each service image from its own directory and Dockerfile.

The root README remains the source of truth for starting the complete system.

## Service structure

Use a clean, hexagonal/layered structure. Keep transport, application logic and persistence separate:

```text
adapter/in        REST controllers, request/response DTOs
application       use cases and transaction boundaries
domain            entities, value objects and business rules
adapter/out       repositories, database adapters and Kafka adapters
configuration     Spring and infrastructure configuration
```

At minimum, the code should have clear equivalents of:

- REST controllers;
- application services/use cases;
- domain models and validation rules;
- repository interfaces;
- database implementations;
- Kafka publisher/consumer adapters.

Do not put business logic in controllers or repository classes.

## Database and ownership rules

The project uses one PostgreSQL database because of the exercise time limit. This is an intentional trade-off, not permission for every service to modify every table.

Logical ownership is:

- Account Service: `user_accounts` and account-creation idempotency;
- Financial Operations Service: `financial_operations` and financial-operation idempotency;
- Outbox Worker: publication state in `financial_operation_result_outbox`;
- Audit Consumer: `financial_operations_audit_events`.

The Financial Operations Service is explicitly allowed to update the balance and financial state in `user_accounts` because balance mutation, validation, history and outbox creation must happen in one PostgreSQL transaction. This is the deliberate shared-database exception documented in `ARCHITECTURE.md`.

Do not introduce a second balance table or duplicate account state.

Use `NUMERIC`/`BigDecimal` for money. Never use floating-point types. Enforce positive operation amounts and a non-negative resulting balance both in application validation and, where practical, with database constraints.

Use ISO-8601 timestamps in UTC at API and event boundaries. Persist timestamps consistently and do not depend on the server's local timezone.

## Transaction rules

Financial commands must use a real database transaction with the correct Spring transaction boundary, for example an application service method annotated with `@Transactional`.

For deposit, withdrawal and transfer, use `TransactionalOperator.executeAndAwait` around the complete suspending command:

1. validate request and identifiers;
2. check idempotency key;
3. lock required account rows using pessimistic locking or use a correctly implemented optimistic-locking strategy;
4. lock transfer accounts in deterministic `account_id` order;
5. validate account status and balance;
6. update balances;
7. insert `financial_operations`;
8. insert `financial_operation_result_outbox` for successful operations;
9. commit the transaction.

Do not publish directly to Kafka before the database transaction commits. Do not update the balance in one transaction and write the operation history or outbox event in another.

Rejected operations may be recorded in `financial_operations` with status `REJECTED`, but they must not create a `FinancialOperationCompleted` outbox event. The implementation must choose this behavior consistently and document it in the API README.

## Idempotency rules

Use the client-provided `Idempotency-Key` as the unique request key. Keep it separate from the generated `account_id` or `operation_id`.

For a repeated request:

- return the original response;
- do not apply the balance mutation again;
- do not create a second financial operation;
- do not create a second outbox event.

Store a request hash. Reusing an idempotency key with a different request body must return a conflict rather than silently reusing the previous result.

Handle concurrent requests with the same idempotency key safely using a unique database constraint and transactionally coordinated state.

## Outbox and Kafka rules

The outbox worker is a separate Spring Boot service. It polls pending outbox rows, publishes JSON events to Redpanda and marks a row as processed only after successful publication.

Use the topic:

```text
financial-operations
```

Use `operation_id` as the partition key. Events must contain at least:

```text
eventId
eventType
operationId
operationType
fromAccountId
toAccountId
amount
occurredAt
```

No Schema Registry is required. Treat the JSON event as an explicit contract, keep field names stable and include `eventType`. Do not rely on undocumented Java serialization.

The consumer must:

- process only successful financial-operation events;
- write the audit event transactionally;
- deduplicate by unique `event_id`;
- retry transient failures;
- expose or document a dead-letter/reprocessing strategy;
- tolerate at-least-once delivery.

Kafka is not the source of truth for operation history. `financial_operations` is available immediately after the financial transaction commits. Audit events are eventually consistent.

## API quality requirements

Follow normal REST and industry API conventions:

- version endpoints under `/api/v1`;
- use resource-oriented URLs;
- validate JSON bodies, UUIDs, amounts and required fields;
- return consistent error responses;
- use appropriate HTTP status codes;
- do not expose persistence entities directly from controllers;
- use request and response DTOs;
- document idempotency behavior;
- document error cases in OpenAPI.

Use guard clauses at the beginning of application methods for cheap preconditions and invalid states. Keep the main business flow readable and avoid deeply nested `if` blocks. Guard clauses must not replace transactional locking or database constraints.

Authentication and authorization are intentionally out of scope for this recruitment MVP. Do not add partial security flows that distract from the required account and financial-operation behavior. Document this limitation in the root README and architecture trade-offs.

## Testing requirements

Tests do not need exhaustive coverage. Prioritize behavior that proves the architecture works:

### Unit tests

Use JUnit 5 and Mockito. Focus on:

- account status transitions;
- amount validation;
- insufficient funds;
- blocked/closed account rejection;
- idempotent repeated requests;
- transfer business rules;
- consumer duplicate handling.

Use descriptive Kotlin test names with a `should` convention, for example:

```kotlin
fun `should reject withdrawal when balance is insufficient`()
fun `should return original response for repeated idempotency key`()
fun `should ignore already processed audit event`()
```

### Integration tests

Use Testcontainers for PostgreSQL and Redpanda/Kafka integration where practical. At minimum, cover:

- happy-path account creation;
- happy-path deposit, withdrawal and transfer;
- concurrent withdrawals or transfers cannot create a negative balance;
- a repeated idempotent request changes the balance only once;
- outbox publication and audit-consumer processing;
- duplicate Kafka event does not create duplicate audit data.

Each major feature should have a happy-path test and no more than three focused error-path tests unless additional cases expose a real correctness risk.

## Code quality rules

- Keep methods small and focused.
- Use meaningful domain names rather than generic `data`, `result` or `helper` names.
- Centralize exception-to-HTTP error mapping.
- Add database indexes and unique constraints that support the actual access paths.
- Avoid speculative abstractions and unnecessary microservices.
- Do not hide important consistency behavior behind implicit framework defaults.
- Add structured logs around operation IDs, event IDs and idempotency keys, without logging sensitive data.
- Keep configuration externalized through environment variables.
- Make Docker startup reproducible from a clean checkout.
- Keep logs minimal and structured around `operationId`, `eventId` and `idempotencyKey`.
- Never log passwords, tokens, personal identification numbers, full request payloads or other sensitive information.

Because application Dockerfiles must copy prebuilt JARs and must not run Gradle, the complete local environment must be runnable from a clean checkout with:

```bash
./gradlew clean build
docker compose up --build
```

Do not claim that Docker Compose compiles application JARs. It starts the complete runtime environment after the host build has produced the artifacts.

Document required environment variables and safe local defaults. Never commit secrets.

## Recommended implementation order

1. Generate the Spring Boot service skeletons and establish the shared project conventions.
2. Add Docker Compose with PostgreSQL and Redpanda.
3. Implement account creation, retrieval and status transitions.
4. Implement financial operations and database locking.
5. Add synchronous operation history and idempotency.
6. Add the transactional outbox and worker.
7. Add the Kafka consumer and audit events.
8. Add unit and Testcontainers integration tests.
9. Add OpenAPI files, Postman collection and service/root READMEs.
10. Run the complete stack from Docker Compose and verify the documented happy/error flows.

When time is limited, prioritize transaction correctness, idempotency, outbox delivery and tests over the optional BFF or mobile client.
