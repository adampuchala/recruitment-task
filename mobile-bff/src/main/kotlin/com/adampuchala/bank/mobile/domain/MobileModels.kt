// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.mobile.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateMobileAccountCommand(val firstName: String, val lastName: String)
data class DepositFundsCommand(val amount: BigDecimal, val description: String?)

enum class AccountStatus { ACTIVE, BLOCKED, CLOSED }

data class MobileAccount(
    val accountId: UUID,
    val firstName: String,
    val lastName: String,
    val balance: BigDecimal,
    val status: AccountStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class MobileDeposit(
    val operationId: UUID,
    val type: String,
    val status: String,
    val fromAccountId: UUID?,
    val toAccountId: UUID,
    val amount: BigDecimal,
    val balanceAfter: BigDecimal,
    val description: String?,
    val createdAt: Instant,
)

data class DownstreamError(val code: String, val message: String)

sealed interface DownstreamResult<out T> {
    data class Success<T>(val statusCode: Int, val body: T) : DownstreamResult<T>
    data class Failure(val statusCode: Int, val error: DownstreamError) : DownstreamResult<Nothing>
}

class DownstreamUnavailableException(service: String, cause: Throwable) : RuntimeException("$service is unavailable", cause)
