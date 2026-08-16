# Financial Operations Service

Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.

Executes deposits, withdrawals and transfers under pessimistic PostgreSQL row locks. Balance, successful history, idempotency response and outbox event commit atomically. Rejected operations are not stored in history and produce no Kafka event. Runs on port `8082`.

The service runs on port `8082`; Swagger UI is available at `/swagger-ui.html` and the static contract is `openapi.yaml`. Configuration: `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `SERVER_PORT`.

From the repository root, run `./gradlew :financial-operations-service:build` or start the complete environment with `docker compose up --build` after the root build. The module build includes PostgreSQL Testcontainers concurrency and rollback tests.
