// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.contracts

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class FinancialOperationCompletedTest {
    private val mapper = jacksonObjectMapper()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @Test
    fun `should preserve event contract during round trip`() {
        val event = FinancialOperationCompleted(
            eventId = UUID.fromString("f4f5b7e7-9f17-4f4a-ae5f-9f0e7d7e4df1"),
            operationId = UUID.fromString("d09c1d2a-6ca0-4cb4-982f-2a0a8c5adf9d"),
            operationType = FinancialOperationType.TRANSFER,
            fromAccountId = UUID.fromString("8a7c9f0d-1b6a-4f2f-9e7f-52e2f9e7d1a2"),
            toAccountId = UUID.fromString("1c1be0f1-5ad9-4b14-83ea-5f18f2e4cc4d"),
            amount = BigDecimal("100.00"),
            occurredAt = Instant.parse("2026-08-16T12:00:00Z"),
        )

        assertEquals(event, mapper.readValue<FinancialOperationCompleted>(mapper.writeValueAsString(event)))
    }
}
