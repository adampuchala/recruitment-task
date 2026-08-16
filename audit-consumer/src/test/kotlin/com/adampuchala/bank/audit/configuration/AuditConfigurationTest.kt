// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.audit.configuration

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.MessageListenerContainer
import org.springframework.kafka.support.SendResult
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

class AuditConfigurationTest {
    @Test
    fun `should retry three times then preserve key and payload on DLT`() {
        val template = mock<KafkaTemplate<String, String>>()
        val consumer = mock<Consumer<String, String>>()
        val container = mock<MessageListenerContainer>()
        whenever(template.isTransactional).thenReturn(false)
        whenever(template.send(any<ProducerRecord<String, String>>()))
            .thenReturn(CompletableFuture.completedFuture(mock<SendResult<String, String>>()))
        val handler = AuditConfiguration().kafkaErrorHandler(template)
        val failedRecord = ConsumerRecord("financial-operations", 1, 7, "operation-key", "malformed-payload")

        repeat(4) {
            handler.handleOne(IllegalStateException("transient failure"), failedRecord, consumer, container)
        }

        val record = argumentCaptor<ProducerRecord<String, String>>()
        verify(template, times(1)).send(record.capture())
        assertEquals("financial-operations.DLT", record.firstValue.topic())
        assertEquals("operation-key", record.firstValue.key())
        assertEquals("malformed-payload", record.firstValue.value())
    }
}
