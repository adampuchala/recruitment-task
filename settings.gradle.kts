// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "hapoalim-recruitment-bank-system"

include(
    "account-service",
    "financial-operations-service",
    "outbox-worker",
    "audit-consumer",
    "shared-contracts",
)
