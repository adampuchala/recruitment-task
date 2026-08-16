// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.contracts

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class FinancialOperationType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER,
}

data class FinancialOperationCompleted(
    val eventId: UUID,
    val eventType: String = EVENT_TYPE,
    val operationId: UUID,
    val operationType: FinancialOperationType,
    val fromAccountId: UUID?,
    val toAccountId: UUID?,
    val amount: BigDecimal,
    val occurredAt: Instant,
) {
    companion object {
        const val EVENT_TYPE = "FINANCIAL_OPERATION_COMPLETED"
    }
}
