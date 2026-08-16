# Account Service

Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.

Owns account creation, retrieval and lifecycle status. Runs on port `8081`. Configuration: `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `SERVER_PORT`. The static contract is `openapi.yaml`; Swagger UI is available at `/swagger-ui.html`.

From the repository root, run `./gradlew :account-service:build` or start the complete environment with `docker compose up --build` after the root build. Tests run as part of `:account-service:build`. The Dockerfile runs the prebuilt `build/libs/account-service.jar`.
