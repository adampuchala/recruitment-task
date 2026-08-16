# Mobile BFF

Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.

Minimal unauthenticated mobile Backend for Frontend. It delegates account creation and account lookup to Account Service, and deposits to Financial Operations Service. It has no database, Kafka consumer or business-state ownership.

Build the repository first, then run the complete stack:

```bash
./gradlew clean build
docker compose up --build
```

The BFF listens on `http://localhost:8080`. Swagger UI is available at `http://localhost:8080/swagger-ui.html`; the generator contract is [openapi.yaml](openapi.yaml).

Configuration:

- `ACCOUNT_SERVICE_BASE_URL` — default `http://localhost:8081`;
- `FINANCIAL_OPERATIONS_SERVICE_BASE_URL` — default `http://localhost:8082`;
- `SERVER_PORT` — default `8080`.

For POST operations the mobile client must generate a UUID `Idempotency-Key` once per user command and reuse it only while retrying that same command.
