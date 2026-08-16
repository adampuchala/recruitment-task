// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.audit.adapter.out

import com.adampuchala.bank.audit.domain.AuditRepository
import com.adampuchala.bank.contracts.FinancialOperationCompleted
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class PostgresAuditRepository(private val databaseClient: DatabaseClient) : AuditRepository {
    override fun insertIfAbsent(event: FinancialOperationCompleted): Mono<Boolean> {
        var spec = databaseClient.sql(
            """INSERT INTO financial_operations_audit_events
               (event_id, operation_id, from_account_id, to_account_id, amount, created_at)
               VALUES (:eventId, :operationId, :fromId, :toId, :amount, :createdAt)
               ON CONFLICT (event_id) DO NOTHING""",
        ).bind("eventId", event.eventId)
            .bind("operationId", event.operationId)
            .bind("amount", event.amount)
            .bind("createdAt", OffsetDateTime.ofInstant(event.occurredAt, ZoneOffset.UTC))
        spec = spec.bindNullable("fromId", event.fromAccountId, UUID::class.java)
            .bindNullable("toId", event.toAccountId, UUID::class.java)
        return spec.fetch().rowsUpdated().map { it == 1L }
    }

    private fun <T : Any> DatabaseClient.GenericExecuteSpec.bindNullable(name: String, value: T?, type: Class<T>): DatabaseClient.GenericExecuteSpec =
        if (value == null) bindNull(name, type) else bind(name, value)
}
