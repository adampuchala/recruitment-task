# Outbox Worker

Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.

Single-replica coroutine-based, non-blocking worker polling pending outbox rows and publishing JSON to `financial-operations`. A crash after broker acknowledgement and before the database update can create a duplicate; the audit consumer is idempotent. Configure with database variables, `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_TOPIC`, `OUTBOX_POLL_DELAY_MS` and `OUTBOX_BATCH_SIZE`.

The worker has no business API. It exposes Actuator health on port `8083`; `openapi.yaml` documents that endpoint. From the repository root, run `./gradlew :outbox-worker:build` or start it with the complete Docker Compose environment after the root build. The Dockerfile runs the prebuilt `build/libs/outbox-worker.jar`.
