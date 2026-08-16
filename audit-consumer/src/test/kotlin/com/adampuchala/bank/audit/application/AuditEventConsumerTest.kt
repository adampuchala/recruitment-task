// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.audit.application

import com.adampuchala.bank.contracts.FinancialOperationCompleted
import com.adampuchala.bank.contracts.FinancialOperationType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Test
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AuditEventConsumerTest {
    private val objectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())

    @Test
    fun `should ignore already processed audit event`() = runTest {
        val eventIds = ConcurrentHashMap.newKeySet<UUID>()
        val handler = object : AuditEventHandler {
            override suspend fun handle(event: FinancialOperationCompleted): Boolean = eventIds.add(event.eventId)
        }
        val consumer = AuditEventConsumer(handler, objectMapper)
        val payload = objectMapper.writeValueAsString(event())

        consumer.consume(payload)
        consumer.consume(payload)

        assertEquals(1, eventIds.size)
    }

    @Test
    fun `should propagate malformed event for retry and DLT handling`() = runTest {
        val consumer = AuditEventConsumer(object : AuditEventHandler {
            override suspend fun handle(event: FinancialOperationCompleted): Boolean = true
        }, objectMapper)

        assertFailsWith<Exception> { consumer.consume("{not-json") }
    }

    @Test
    fun `should reject incomplete or semantically invalid event for retry and DLT handling`() = runTest {
        val consumer = AuditEventConsumer(object : AuditEventHandler {
            override suspend fun handle(event: FinancialOperationCompleted): Boolean = true
        }, objectMapper)
        val invalidDeposit = event().copy(toAccountId = null)

        assertFailsWith<IllegalArgumentException> { consumer.consume(objectMapper.writeValueAsString(invalidDeposit)) }
        assertFailsWith<IllegalArgumentException> {
            consumer.consume(objectMapper.writeValueAsString(event()).replace("\"eventType\":\"FINANCIAL_OPERATION_COMPLETED\",", ""))
        }
    }

    private fun event() = FinancialOperationCompleted(
        eventId = UUID.randomUUID(),
        operationId = UUID.randomUUID(),
        operationType = FinancialOperationType.DEPOSIT,
        fromAccountId = null,
        toAccountId = UUID.randomUUID(),
        amount = BigDecimal("10.0000"),
        occurredAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
}
