// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.account.application

import com.adampuchala.bank.account.domain.Account
import com.adampuchala.bank.account.domain.AccountRepository
import com.adampuchala.bank.account.domain.AccountStatus
import com.adampuchala.bank.account.domain.AccountPolicy
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

data class AccountView(
    val accountId: UUID,
    val firstName: String,
    val lastName: String,
    val balance: BigDecimal,
    val status: AccountStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class CreationResult(val account: AccountView, val replay: Boolean)

class AccountNotFoundException : RuntimeException("Account not found")
class InvalidStatusTransitionException(message: String) : RuntimeException(message)
class NonZeroBalanceException : RuntimeException("An account can be closed only with a zero balance")
class IdempotencyConflictException : RuntimeException("Idempotency key was already used with another request")

@Service
class AccountApplicationService(
    private val repository: AccountRepository,
    private val objectMapper: ObjectMapper,
    private val transactionalOperator: TransactionalOperator,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun create(idempotencyKey: UUID, firstName: String, lastName: String): CreationResult {
        val normalizedFirst = firstName.trim()
        val normalizedLast = lastName.trim()
        val hash = sha256("$normalizedFirst\u0000$normalizedLast")
        return transactionalOperator.executeAndAwait {
            if (repository.tryInsertIdempotency(idempotencyKey, hash)) {
                createNew(idempotencyKey, normalizedFirst, normalizedLast)
            } else {
                replay(idempotencyKey, hash)
            }
        }
    }

    suspend fun get(accountId: UUID): AccountView = repository.findById(accountId)
        ?.toView()
        ?: throw AccountNotFoundException()

    suspend fun changeStatus(accountId: UUID, target: AccountStatus): AccountView = transactionalOperator.executeAndAwait {
        val account = repository.findByIdForUpdate(accountId)
            ?: throw AccountNotFoundException()
        validateTransition(account, target)
        if (account.status == target) {
            account.toView()
        } else {
            repository.updateStatus(accountId, target, clock.instant())
            (repository.findById(accountId) ?: throw AccountNotFoundException()).toView()
        }
    }

    private suspend fun createNew(key: UUID, firstName: String, lastName: String): CreationResult {
        val now = clock.instant()
        val account = Account(UUID.randomUUID(), firstName, lastName, BigDecimal.ZERO.setScale(4), AccountStatus.ACTIVE, 0, now, now)
        val view = account.toView()
        val payload = objectMapper.writeValueAsString(view)
        repository.insert(account)
        repository.completeIdempotency(key, account.accountId, payload)
        return CreationResult(view, false)
    }

    private suspend fun replay(key: UUID, hash: String): CreationResult {
        val record = repository.findIdempotency(key)
            ?: throw IllegalStateException("Idempotency request is incomplete")
        if (record.requestHash != hash) throw IdempotencyConflictException()
        if (record.status != "SUCCESS" || record.responsePayload == null) {
            throw IllegalStateException("Idempotency request is incomplete")
        }
        return CreationResult(objectMapper.readValue(record.responsePayload, AccountView::class.java), true)
    }

    private fun validateTransition(account: Account, target: AccountStatus) {
        if (account.status == AccountStatus.CLOSED) throw InvalidStatusTransitionException("Closed account status cannot be changed")
        if (target == AccountStatus.CLOSED && account.balance.compareTo(BigDecimal.ZERO) != 0) throw NonZeroBalanceException()
        if (!AccountPolicy.transitionAllowed(account.status, target, account.balance)) throw InvalidStatusTransitionException("Invalid account status transition")
    }

    private fun Account.toView() = AccountView(accountId, firstName, lastName, balance, status, createdAt, updatedAt)

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
