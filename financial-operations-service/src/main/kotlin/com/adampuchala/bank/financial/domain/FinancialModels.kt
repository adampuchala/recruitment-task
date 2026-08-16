// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.financial.domain

import com.adampuchala.bank.contracts.FinancialOperationType
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class AccountStatus { ACTIVE, BLOCKED, CLOSED }

data class LockedAccount(
    val accountId: UUID,
    val balance: BigDecimal,
    val status: AccountStatus,
    val version: Long,
)

data class FinancialOperation(
    val operationId: UUID,
    val type: FinancialOperationType,
    val fromAccountId: UUID?,
    val toAccountId: UUID?,
    val amount: BigDecimal,
    val status: String,
    val description: String?,
    val createdAt: Instant,
)

data class FinancialIdempotencyRecord(
    val requestHash: String,
    val responsePayload: String?,
    val status: String,
)
