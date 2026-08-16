// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.account.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class AccountStatus { ACTIVE, BLOCKED, CLOSED }

data class Account(
    val accountId: UUID,
    val firstName: String,
    val lastName: String,
    val balance: BigDecimal,
    val status: AccountStatus,
    val version: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class AccountIdempotencyRecord(
    val idempotencyKey: UUID,
    val accountId: UUID?,
    val requestHash: String,
    val responsePayload: String?,
    val status: String,
)
