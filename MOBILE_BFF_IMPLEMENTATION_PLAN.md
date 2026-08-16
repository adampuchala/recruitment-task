# Mobile BFF implementation plan

Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.

## Scope

Implement only the mobile flows shown in the architecture and required by the optional mobile client:

- create an account;
- retrieve account details;
- deposit funds.

Authentication, authorization, account-status changes, withdrawals, transfers and operation history are outside this BFF scope.

## Service

Create a `mobile-bff` Spring Boot service using the same stack as the backend:

- Kotlin and Java 25;
- Spring Boot 4.1.0;
- WebFlux, Netty and Kotlin coroutines;
- suspending `WebClient` calls;
- no database, Liquibase, Kafka or local business state;
- port `8080`;
- Dockerfile that runs a previously built JAR.

Use environment variables:

- `ACCOUNT_SERVICE_BASE_URL=http://account-service:8081`;
- `FINANCIAL_OPERATIONS_SERVICE_BASE_URL=http://financial-operations-service:8082`;
- `SERVER_PORT=8080`.

## API and delegation

The public contract is [mobile-bff/openapi.yaml](mobile-bff/openapi.yaml).

| BFF endpoint | Delegates to |
|---|---|
| `POST /api/v1/mobile/accounts` | Account Service `POST /api/v1/accounts` |
| `GET /api/v1/mobile/accounts/{accountId}` | Account Service `GET /api/v1/accounts/{accountId}` |
| `POST /api/v1/mobile/accounts/{accountId}/deposits` | Financial Operations Service `POST /api/v1/accounts/{accountId}/deposits` |

Forward `Idempotency-Key` unchanged for both POST operations. Do not automatically retry POST requests in the first version. Preserve downstream `200`, `201`, `400`, `404` and `409` responses and map unavailable downstream services to `503 DOWNSTREAM_UNAVAILABLE`.

## Code structure

- `adapter/in/MobileController` — implements the OpenAPI contract;
- `application/MobileAccountFacade` — orchestration with no financial business rules;
- `port/out/AccountServiceClient` and `FinancialOperationsClient` — downstream ports;
- `adapter/out/WebClientAccountServiceClient` and `WebClientFinancialOperationsClient` — HTTP adapters;
- `adapter/in/ApiErrorHandler` — stable mobile error model.

Keep downstream DTOs separate from public BFF DTOs so backend contracts can evolve without leaking directly into the generated mobile SDK.

## Minimal tests and delivery

1. Unit-test delegation, header forwarding and downstream error mapping with mocked ports.
2. Add a WebFlux controller test for the three endpoints.
3. Validate `openapi.yaml` during the Gradle `check` task.
4. Add the service to Gradle settings and Docker Compose with an Actuator healthcheck.
5. Document Swagger UI and mobile OpenAPI links in the root README.

Definition of Done: `./gradlew clean build`, `docker compose up --build`, all containers healthy, and the three BFF calls work against the existing services.
