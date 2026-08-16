// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.audit.application

import com.adampuchala.bank.audit.domain.AuditRepository
import com.adampuchala.bank.contracts.FinancialOperationCompleted
import com.adampuchala.bank.contracts.FinancialOperationType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals

class AuditEventConsumerTest {
    private val objectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())

    @Test
    fun `should ignore already processed audit event`() {
        val eventIds = ConcurrentHashMap.newKeySet<UUID>()
        val repository = object : AuditRepository {
            override fun insertIfAbsent(event: FinancialOperationCompleted): Mono<Boolean> =
                Mono.fromSupplier { eventIds.add(event.eventId) }
        }
        val transaction = passThroughTransaction()
        val consumer = AuditEventConsumer(repository, objectMapper, transaction)
        val payload = objectMapper.writeValueAsString(event())

        StepVerifier.create(consumer.consume(payload).then(consumer.consume(payload))).verifyComplete()

        assertEquals(1, eventIds.size)
    }

    @Test
    fun `should propagate malformed event for retry and DLT handling`() {
        val repository = mock<AuditRepository>()
        val consumer = AuditEventConsumer(repository, objectMapper, passThroughTransaction())

        StepVerifier.create(consumer.consume("{not-json"))
            .expectError()
            .verify()
    }

    private fun passThroughTransaction(): TransactionalOperator {
        val transaction = mock<TransactionalOperator>()
        whenever(transaction.transactional(any<Mono<Boolean>>())).thenAnswer { it.arguments[0] }
        return transaction
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
