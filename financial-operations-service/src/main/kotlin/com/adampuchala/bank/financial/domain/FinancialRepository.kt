// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.financial.domain

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

interface FinancialRepository {
    suspend fun tryInsertIdempotency(key: UUID, hash: String): Boolean
    suspend fun findIdempotency(key: UUID): FinancialIdempotencyRecord?
    suspend fun completeSuccess(key: UUID, operationId: UUID, payload: String)
    suspend fun completeError(key: UUID, payload: String)
    suspend fun lockAccount(accountId: UUID): LockedAccount?
    suspend fun updateBalance(accountId: UUID, balance: BigDecimal, updatedAt: Instant)
    suspend fun insertOperation(operation: FinancialOperation)
    suspend fun insertOutbox(eventId: UUID, operationId: UUID, payload: String, createdAt: Instant)
    suspend fun findOperation(operationId: UUID): FinancialOperation?
    suspend fun findByAccount(accountId: UUID, limit: Int, offset: Long): List<FinancialOperation>
    suspend fun countByAccount(accountId: UUID): Long
}
