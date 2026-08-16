// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.outbox.domain

import java.time.Instant
import java.util.UUID

interface OutboxRepository {
    suspend fun findPending(limit: Int): List<OutboxEvent>
    suspend fun markProcessed(eventId: UUID, processedAt: Instant)
    suspend fun incrementAttempts(eventId: UUID)
}
