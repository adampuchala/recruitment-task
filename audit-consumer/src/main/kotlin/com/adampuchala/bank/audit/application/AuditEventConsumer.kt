// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.audit.application

import com.adampuchala.bank.contracts.FinancialOperationCompleted
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class AuditEventConsumer(
    private val handler: AuditEventHandler,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["\${audit.topic:financial-operations}"], groupId = "\${audit.group-id:financial-operations-audit}")
    suspend fun consume(payload: String) {
        val event = objectMapper.readValue(payload, FinancialOperationCompleted::class.java)
        require(event.eventType == FinancialOperationCompleted.EVENT_TYPE) { "Unsupported event type" }
        val inserted = handler.handle(event)
        if (inserted) log.info("Stored audit event eventId={} operationId={}", event.eventId, event.operationId)
        else log.info("Ignored duplicate audit event eventId={}", event.eventId)
    }
}
