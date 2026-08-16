// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.spring") version "2.3.0" apply false
    id("org.springframework.boot") version "4.1.0" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
}

group = "com.adampuchala"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(25)
}

tasks.test {
    useJUnitPlatform()
}

val validateOpenApi by tasks.registering {
    group = "verification"
    description = "Validates the static OpenAPI documents and their required paths."

    doLast {
        val requiredPaths = mapOf(
            "account-service/openapi.yaml" to setOf(
                "/api/v1/accounts:",
                "/api/v1/accounts/{accountId}:",
                "/api/v1/accounts/{accountId}/status:",
            ),
            "financial-operations-service/openapi.yaml" to setOf(
                "/api/v1/accounts/{accountId}/deposits:",
                "/api/v1/accounts/{accountId}/withdrawals:",
                "/api/v1/transfers:",
                "/api/v1/accounts/{accountId}/operations:",
                "/api/v1/operations/{operationId}:",
            ),
            "outbox-worker/openapi.yaml" to setOf("/actuator/health:"),
            "audit-consumer/openapi.yaml" to setOf("/actuator/health:"),
            "mobile-bff/openapi.yaml" to setOf(
                "/api/v1/mobile/accounts:",
                "/api/v1/mobile/accounts/{accountId}:",
                "/api/v1/mobile/accounts/{accountId}/deposits:",
            ),
        )

        requiredPaths.forEach { (relativePath, paths) ->
            val specification = rootProject.file(relativePath)
            check(specification.isFile) { "Missing OpenAPI specification: $relativePath" }
            val content = specification.readText()
            check(Regex("(?m)^openapi: 3\\.[01]\\.").containsMatchIn(content)) {
                "Invalid or unsupported OpenAPI version in $relativePath"
            }
            paths.forEach { path ->
                check(content.lineSequence().any { it.trim() == path }) {
                    "Missing documented path $path in $relativePath"
                }
            }
        }
    }
}

tasks.named("check") {
    dependsOn(validateOpenApi)
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
