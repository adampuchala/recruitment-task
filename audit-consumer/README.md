# Audit Consumer

Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.

Consumes successful financial-operation events and writes one audit row per `eventId`. Transient failures retry three times with one-second backoff, then publish to `financial-operations.DLT`.

The consumer has no business API. It exposes Actuator health on port `8084`; `openapi.yaml` documents that endpoint. Configure it with the database variables plus `KAFKA_BOOTSTRAP_SERVERS`, `KAFKA_TOPIC`, `KAFKA_CONSUMER_GROUP` and `SERVER_PORT`. From the repository root, run `./gradlew :audit-consumer:build` or start it with the complete Docker Compose environment after the root build.

Inspect DLT messages with:

```bash
docker compose exec redpanda rpk topic consume financial-operations.DLT -X brokers=redpanda:9092 -o start
```

After correcting the cause, republish the unchanged key and JSON payload to the main topic:

```bash
printf '%s\n' '<unchanged-json-payload>' | docker compose exec -T redpanda \
  rpk topic produce financial-operations -X brokers=redpanda:9092 -k '<original-key>'
```

Do not automatically loop DLT records into the main topic. Inspect the failure first. Duplicate replay remains safe because `event_id` is the audit primary key.
