// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.financial.application

import com.adampuchala.bank.contracts.FinancialOperationCompleted
import com.adampuchala.bank.contracts.FinancialOperationType
import com.adampuchala.bank.financial.domain.AccountStatus
import com.adampuchala.bank.financial.domain.FinancialOperation
import com.adampuchala.bank.financial.domain.FinancialRepository
import com.adampuchala.bank.financial.domain.FinancialRules
import com.adampuchala.bank.financial.domain.LockedAccount
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import java.math.BigDecimal
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

data class OperationResponse(
    val operationId: UUID,
    val type: FinancialOperationType,
    val status: String,
    val fromAccountId: UUID?,
    val toAccountId: UUID?,
    val amount: BigDecimal,
    val balanceAfter: BigDecimal?,
    val description: String?,
    val createdAt: Instant,
)

data class StoredBusinessError(val code: String, val message: String, val timestamp: Instant)

sealed interface CommandResult {
    data class Accepted(val response: OperationResponse, val replay: Boolean = false) : CommandResult
    data class Rejected(val error: StoredBusinessError, val replay: Boolean = false) : CommandResult
}

private sealed interface IdempotencyAcquisition {
    data object NewRequest : IdempotencyAcquisition
    data class Existing(val result: CommandResult) : IdempotencyAcquisition
}

data class OperationPage(val items: List<OperationResponse>, val page: Int, val size: Int, val totalElements: Long)

class AccountNotFoundException : RuntimeException("Account not found")
class OperationNotFoundException : RuntimeException("Operation not found")
class IdempotencyConflictException : RuntimeException("Idempotency key was already used with another request")
class InvalidFinancialRequestException(message: String) : RuntimeException(message)

@Service
class FinancialApplicationService(
    private val repository: FinancialRepository,
    private val objectMapper: ObjectMapper,
    private val transactionalOperator: TransactionalOperator,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun deposit(key: UUID, accountId: UUID, amount: BigDecimal, description: String?): CommandResult {
        guardRequest(amount, description)
        return executeSingle(key, FinancialOperationType.DEPOSIT, accountId, amount, description)
    }

    suspend fun withdrawal(key: UUID, accountId: UUID, amount: BigDecimal, description: String?): CommandResult {
        guardRequest(amount, description)
        return executeSingle(key, FinancialOperationType.WITHDRAWAL, accountId, amount, description)
    }

    suspend fun transfer(key: UUID, fromId: UUID, toId: UUID, amount: BigDecimal, description: String?): CommandResult {
        guardRequest(amount, description)
        val hash = requestHash(FinancialOperationType.TRANSFER, fromId, toId, amount, description)
        return transactionalOperator.executeAndAwait {
            when (val acquisition = acquire(key, hash)) {
                is IdempotencyAcquisition.Existing -> acquisition.result
                IdempotencyAcquisition.NewRequest -> {
                    if (fromId == toId) {
                        reject(key, "INVALID_TRANSFER", "Source and destination accounts must differ")
                    } else {
                        val sorted = listOf(fromId, toId).sortedBy(UUID::toString) //avoid deadlock when locking accounts in different order
                        val first = repository.lockAccount(sorted[0]) ?: throw AccountNotFoundException()
                        val second = repository.lockAccount(sorted[1]) ?: throw AccountNotFoundException()
                        val from = if (first.accountId == fromId) first else second
                        val to = if (first.accountId == toId) first else second
                        validateActive(from)?.let { return@executeAndAwait reject(key, it.code, it.message) }
                        validateActive(to)?.let { return@executeAndAwait reject(key, it.code, it.message) }
                        if (from.balance < amount) {
                            reject(key, "INSUFFICIENT_FUNDS", "The account balance is insufficient for this operation")
                        } else {
                            persistSuccess(
                                key, FinancialOperationType.TRANSFER, fromId, toId, amount, description, null,
                            ) {
                                repository.updateBalance(fromId, from.balance.subtract(amount), clock.instant())
                                repository.updateBalance(toId, to.balance.add(amount), clock.instant())
                            }
                        }
                    }
                }
            }
        }
    }

    suspend fun get(operationId: UUID): OperationResponse = (repository.findOperation(operationId)
        ?: throw OperationNotFoundException()).toResponse(null)

    suspend fun history(accountId: UUID, page: Int, size: Int): OperationPage {
        if (page < 0 || size !in 1..100) throw InvalidFinancialRequestException("page must be at least 0 and size must be between 1 and 100")
        val operations = repository.findByAccount(accountId, size, page.toLong() * size)
        val total = repository.countByAccount(accountId)
        return OperationPage(operations.map { it.toResponse(null) }, page, size, total)
    }

    private suspend fun executeSingle(key: UUID, type: FinancialOperationType, accountId: UUID, amount: BigDecimal, description: String?): CommandResult =
        transactionalOperator.executeAndAwait {
        val fromId = if (type == FinancialOperationType.WITHDRAWAL) accountId else null
        val toId = if (type == FinancialOperationType.DEPOSIT) accountId else null
        val hash = requestHash(type, fromId, toId, amount, description)
        when (val acquisition = acquire(key, hash)) {
            is IdempotencyAcquisition.Existing -> acquisition.result
            IdempotencyAcquisition.NewRequest -> {
                val account = repository.lockAccount(accountId) ?: throw AccountNotFoundException()
                validateActive(account)?.let { return@executeAndAwait reject(key, it.code, it.message) }
                val newBalance = if (type == FinancialOperationType.DEPOSIT) account.balance.add(amount) else account.balance.subtract(amount)
                if (newBalance.signum() < 0) {
                    reject(key, "INSUFFICIENT_FUNDS", "The account balance is insufficient for this operation")
                } else {
                    persistSuccess(key, type, fromId, toId, amount, description, newBalance) {
                        repository.updateBalance(accountId, newBalance, clock.instant())
                    }
                }
            }
        }
    }

    private suspend fun persistSuccess(
        key: UUID,
        type: FinancialOperationType,
        fromId: UUID?,
        toId: UUID?,
        amount: BigDecimal,
        description: String?,
        balanceAfter: BigDecimal?,
        balanceUpdates: suspend () -> Unit,
    ): CommandResult {
        val now = clock.instant()
        val operation = FinancialOperation(UUID.randomUUID(), type, fromId, toId, amount, "SUCCESS", description?.trim(), now)
        val event = FinancialOperationCompleted(UUID.randomUUID(), operationId = operation.operationId, operationType = type,
            fromAccountId = fromId, toAccountId = toId, amount = amount, occurredAt = now)
        val response = operation.toResponse(balanceAfter)
        balanceUpdates()
        repository.insertOperation(operation)
        repository.insertOutbox(event.eventId, operation.operationId, objectMapper.writeValueAsString(event), now)
        repository.completeSuccess(key, operation.operationId, objectMapper.writeValueAsString(response))
        return CommandResult.Accepted(response)
    }

    private suspend fun acquire(key: UUID, hash: String): IdempotencyAcquisition {
        if (repository.tryInsertIdempotency(key, hash)) return IdempotencyAcquisition.NewRequest
        val record = repository.findIdempotency(key)
            ?: throw IllegalStateException("Idempotency request is incomplete")
        if (record.requestHash != hash) throw IdempotencyConflictException()
        return when {
            record.status == "SUCCESS" && record.responsePayload != null ->
                IdempotencyAcquisition.Existing(CommandResult.Accepted(objectMapper.readValue(record.responsePayload, OperationResponse::class.java), true))
            record.status == "ERROR" && record.responsePayload != null ->
                IdempotencyAcquisition.Existing(CommandResult.Rejected(objectMapper.readValue(record.responsePayload, StoredBusinessError::class.java), true))
            else -> throw IllegalStateException("Idempotency request is incomplete")
        }
    }

    private suspend fun reject(key: UUID, code: String, message: String): CommandResult {
        val error = StoredBusinessError(code, message, clock.instant())
        repository.completeError(key, objectMapper.writeValueAsString(error))
        return CommandResult.Rejected(error)
    }

    private fun validateActive(account: LockedAccount): StoredBusinessError? = when (account.status) {
        AccountStatus.ACTIVE -> null
        AccountStatus.BLOCKED -> StoredBusinessError("ACCOUNT_BLOCKED", "The account is blocked", clock.instant())
        AccountStatus.CLOSED -> StoredBusinessError("ACCOUNT_CLOSED", "The account is closed", clock.instant())
    }

    private fun guardRequest(amount: BigDecimal, description: String?) {
        if (!FinancialRules.validAmount(amount)) throw InvalidFinancialRequestException("Amount must be positive with at most four fractional digits")
        if (description != null && description.length > 500) throw InvalidFinancialRequestException("Description must not exceed 500 characters")
    }

    private fun FinancialOperation.toResponse(balanceAfter: BigDecimal?) = OperationResponse(
        operationId, type, status, fromAccountId, toAccountId, amount, balanceAfter, description, createdAt,
    )

    private fun requestHash(type: FinancialOperationType, fromId: UUID?, toId: UUID?, amount: BigDecimal, description: String?): String {
        val canonical = listOf(type.name, fromId?.toString().orEmpty(), toId?.toString().orEmpty(), amount.stripTrailingZeros().toPlainString(), description?.trim().orEmpty()).joinToString("\u0000")
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
