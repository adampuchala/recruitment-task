// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.outbox.application

import com.adampuchala.bank.outbox.domain.OutboxEvent
import com.adampuchala.bank.outbox.domain.OutboxRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.future.await
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class OutboxPublisher(
    private val repository: OutboxRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @param:Value("\${outbox.topic:financial-operations}") private val topic: String,
    @param:Value("\${outbox.batch-size:50}") private val batchSize: Int,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${outbox.poll-delay-ms:500}")
    suspend fun publishPending() {
        for (event in repository.findPending(batchSize)) {
            publishOne(event)
        }
    }

    private suspend fun publishOne(event: OutboxEvent) {
        try {
            kafkaTemplate.send(topic, event.operationId.toString(), event.payload).await()
            repository.markProcessed(event.eventId, clock.instant())
            log.info("Published outbox event eventId={} operationId={}", event.eventId, event.operationId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            log.warn("Outbox publication failed eventId={} operationId={} reason={}", event.eventId, event.operationId, error.javaClass.simpleName)
            repository.incrementAttempts(event.eventId)
        }
    }
}
