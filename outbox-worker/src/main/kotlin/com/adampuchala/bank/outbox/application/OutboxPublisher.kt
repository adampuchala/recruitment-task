// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.outbox.application

import com.adampuchala.bank.outbox.domain.OutboxEvent
import com.adampuchala.bank.outbox.domain.OutboxRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.Clock

@Service
class OutboxPublisher(
    private val repository: OutboxRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Value("\${outbox.topic:financial-operations}") private val topic: String,
    @Value("\${outbox.batch-size:50}") private val batchSize: Int,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${outbox.poll-delay-ms:500}")
    fun publishPending(): Mono<Void> = repository.findPending(batchSize).concatMap(::publishOne).then()

    private fun publishOne(event: OutboxEvent): Mono<Void> = Mono.fromFuture {
        kafkaTemplate.send(topic, event.operationId.toString(), event.payload)
    }.flatMap { repository.markProcessed(event.eventId, clock.instant()) }
        .doOnSuccess { log.info("Published outbox event eventId={} operationId={}", event.eventId, event.operationId) }
        .onErrorResume { error ->
            log.warn("Outbox publication failed eventId={} operationId={} reason={}", event.eventId, event.operationId, error.javaClass.simpleName)
            repository.incrementAttempts(event.eventId)
        }
}
