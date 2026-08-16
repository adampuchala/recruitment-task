# Recruitment Bank System

Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.

A Kotlin/Java 25 coroutine-based, non-blocking banking backend with PostgreSQL, Redpanda Kafka, transactional outbox, idempotent requests and an asynchronous audit consumer.

## Prerequisites

- Java 25
- Docker Desktop with Docker Compose
- Postman (optional for the included demonstration collection)

## Build and run

Dockerfiles intentionally contain only the runtime JRE and a prebuilt JAR. Build artifacts first, then start the complete runtime stack:

```bash
./gradlew clean build
docker compose up --build
```

Stop with `docker compose down`. Use `docker compose down -v` only when you intentionally want to remove local database data.

## URLs

- Account API: `http://localhost:8081/api/v1/accounts`
- Account Swagger UI: `http://localhost:8081/swagger-ui.html`
- Financial API: `http://localhost:8082/api/v1`
- Financial Swagger UI: `http://localhost:8082/swagger-ui.html`
- Health: ports `8081`–`8084`, path `/actuator/health`
- PostgreSQL: `localhost:5432`
- Redpanda Kafka: `localhost:19092`

## Demonstration

Import `postman/bank-system.postman_collection.json` and `postman/local.postman_environment.json`. Run the collection in order. It creates two accounts, performs deposit/transfer/withdrawal, reads history, demonstrates idempotent replay and verifies error scenarios.

## Architecture

See [ARCHITECTURE.md](ARCHITECTURE.md), [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md), [COROUTINES_MIGRATION_PLAN.md](COROUTINES_MIGRATION_PLAN.md) and the linked Draw.io diagram. The principal consistency boundary is one PostgreSQL transaction containing balance changes, operation history, idempotency state and an outbox event. Kafka audit data is eventually consistent.

## Trade-offs

The recruitment scope intentionally uses one shared database, no authentication/authorization, no Redis, no CQRS, no separate ledger and no Schema Registry. The outbox worker runs as one replica because the fixed schema has no claim/lease state. JSON event contracts and tests provide compatibility within this exercise.

## Tests

```bash
./gradlew test
```

Integration verification requires Docker for PostgreSQL/Redpanda Testcontainers and the Compose end-to-end flow.
