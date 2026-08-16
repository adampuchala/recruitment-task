// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.audit.application

import com.adampuchala.bank.audit.domain.AuditRepository
import com.adampuchala.bank.contracts.FinancialOperationCompleted
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono

@Service
class AuditEventConsumer(
    private val repository: AuditRepository,
    private val objectMapper: ObjectMapper,
    private val transactionalOperator: TransactionalOperator,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["\${audit.topic:financial-operations}"], groupId = "\${audit.group-id:financial-operations-audit}")
    fun consume(payload: String): Mono<Void> = Mono.fromCallable {
        objectMapper.readValue(payload, FinancialOperationCompleted::class.java)
    }.flatMap { event ->
        require(event.eventType == FinancialOperationCompleted.EVENT_TYPE) { "Unsupported event type" }
        transactionalOperator.transactional(repository.insertIfAbsent(event))
            .doOnNext { inserted ->
                if (inserted) log.info("Stored audit event eventId={} operationId={}", event.eventId, event.operationId)
                else log.info("Ignored duplicate audit event eventId={}", event.eventId)
            }.then()
    }
}
