// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.outbox.domain

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

interface OutboxRepository {
    fun findPending(limit: Int): Flux<OutboxEvent>
    fun markProcessed(eventId: UUID, processedAt: Instant): Mono<Void>
    fun incrementAttempts(eventId: UUID): Mono<Void>
}
