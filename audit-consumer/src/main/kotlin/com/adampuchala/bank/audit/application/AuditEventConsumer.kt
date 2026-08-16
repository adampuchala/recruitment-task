// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.audit.application

import com.adampuchala.bank.contracts.FinancialOperationCompleted
import com.adampuchala.bank.contracts.FinancialOperationType
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
        val node = objectMapper.readTree(payload)
        require(node.hasNonNull("eventType")) { "eventType is required" }
        val event = objectMapper.treeToValue(node, FinancialOperationCompleted::class.java)
        validate(event)
        val inserted = handler.handle(event)
        if (inserted) log.info("Stored audit event eventId={} operationId={}", event.eventId, event.operationId)
        else log.info("Ignored duplicate audit event eventId={}", event.eventId)
    }

    private fun validate(event: FinancialOperationCompleted) {
        require(event.eventType == FinancialOperationCompleted.EVENT_TYPE) { "Unsupported event type" }
        require(event.amount.signum() > 0 && event.amount.scale() <= 4) { "amount must be positive with at most four fractional digits" }
        when (event.operationType) {
            FinancialOperationType.DEPOSIT -> require(event.fromAccountId == null && event.toAccountId != null) {
                "Deposit event must contain only toAccountId"
            }
            FinancialOperationType.WITHDRAWAL -> require(event.fromAccountId != null && event.toAccountId == null) {
                "Withdrawal event must contain only fromAccountId"
            }
            FinancialOperationType.TRANSFER -> require(
                event.fromAccountId != null && event.toAccountId != null && event.fromAccountId != event.toAccountId,
            ) { "Transfer event must contain distinct source and destination account IDs" }
        }
    }
}
