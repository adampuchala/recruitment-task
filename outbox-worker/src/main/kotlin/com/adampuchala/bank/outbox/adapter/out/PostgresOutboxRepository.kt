// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.outbox.adapter.out

import com.adampuchala.bank.outbox.domain.OutboxEvent
import com.adampuchala.bank.outbox.domain.OutboxRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitRowsUpdated
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class PostgresOutboxRepository(private val databaseClient: DatabaseClient) : OutboxRepository {
    override suspend fun findPending(limit: Int): List<OutboxEvent> = databaseClient.sql(
        """SELECT event_id, operation_id, payload::text AS payload, attempts
           FROM financial_operation_result_outbox WHERE status = 'PENDING'
           ORDER BY created_at LIMIT :limit""",
    ).bind("limit", limit).map { row, _ ->
        OutboxEvent(
            row.get("event_id", UUID::class.java)!!,
            row.get("operation_id", UUID::class.java)!!,
            row.get("payload", String::class.java)!!,
            row.get("attempts", Integer::class.java)!!.toInt(),
        )
    }.all().asFlow().toList()

    override suspend fun markProcessed(eventId: UUID, processedAt: Instant) {
        databaseClient.sql(
        """UPDATE financial_operation_result_outbox SET status = 'PROCESSED', processed_at = :processedAt
           WHERE event_id = :eventId AND status = 'PENDING'""",
    ).bind("processedAt", OffsetDateTime.ofInstant(processedAt, ZoneOffset.UTC)).bind("eventId", eventId)
        .fetch().awaitRowsUpdated()
    }

    override suspend fun incrementAttempts(eventId: UUID) {
        databaseClient.sql(
        "UPDATE financial_operation_result_outbox SET attempts = attempts + 1 WHERE event_id = :eventId AND status = 'PENDING'",
        ).bind("eventId", eventId).fetch().awaitRowsUpdated()
    }
}
