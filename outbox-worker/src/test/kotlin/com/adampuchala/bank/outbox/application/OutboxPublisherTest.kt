// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.outbox.application

import com.adampuchala.bank.outbox.domain.OutboxEvent
import com.adampuchala.bank.outbox.domain.OutboxRepository
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import kotlinx.coroutines.test.runTest
import java.util.UUID
import java.util.concurrent.CompletableFuture

class OutboxPublisherTest {
    private val repository = mock<OutboxRepository>()
    private val kafkaTemplate = mock<KafkaTemplate<String, String>>()

    @Test
    fun `should mark acknowledged event as processed`() = runTest {
        val event = event()
        val metadata = RecordMetadata(TopicPartition("financial-operations", 0), 0, 0, 0, 0, 0)
        val sendResult = SendResult(ProducerRecord("financial-operations", event.operationId.toString(), event.payload), metadata)
        whenever(repository.findPending(50)).thenReturn(listOf(event))
        whenever(kafkaTemplate.send("financial-operations", event.operationId.toString(), event.payload))
            .thenReturn(CompletableFuture.completedFuture(sendResult))
        whenever(repository.markProcessed(eq(event.eventId), any())).thenReturn(Unit)

        OutboxPublisher(repository, kafkaTemplate, "financial-operations", 50).publishPending()

        verify(repository).markProcessed(eq(event.eventId), any())
        verify(repository, never()).incrementAttempts(event.eventId)
    }

    @Test
    fun `should increment attempts and leave pending after publication failure`() = runTest {
        val event = event()
        val failure = CompletableFuture<SendResult<String, String>>()
        failure.completeExceptionally(IllegalStateException("broker unavailable"))
        whenever(repository.findPending(50)).thenReturn(listOf(event))
        whenever(kafkaTemplate.send("financial-operations", event.operationId.toString(), event.payload)).thenReturn(failure)
        whenever(repository.incrementAttempts(event.eventId)).thenReturn(Unit)

        OutboxPublisher(repository, kafkaTemplate, "financial-operations", 50).publishPending()

        verify(repository).incrementAttempts(event.eventId)
        verify(repository, never()).markProcessed(eq(event.eventId), any())
    }

    private fun event() = OutboxEvent(
        eventId = UUID.randomUUID(),
        operationId = UUID.randomUUID(),
        payload = "{\"eventType\":\"FINANCIAL_OPERATION_COMPLETED\"}",
        attempts = 0,
    )
}
