// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.financial.domain

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

interface FinancialRepository {
    fun tryInsertIdempotency(key: UUID, hash: String): Mono<Boolean>
    fun findIdempotency(key: UUID): Mono<FinancialIdempotencyRecord>
    fun completeSuccess(key: UUID, operationId: UUID, payload: String): Mono<Void>
    fun completeError(key: UUID, payload: String): Mono<Void>
    fun lockAccount(accountId: UUID): Mono<LockedAccount>
    fun updateBalance(accountId: UUID, balance: BigDecimal, updatedAt: Instant): Mono<Void>
    fun insertOperation(operation: FinancialOperation): Mono<Void>
    fun insertOutbox(eventId: UUID, operationId: UUID, payload: String, createdAt: Instant): Mono<Void>
    fun findOperation(operationId: UUID): Mono<FinancialOperation>
    fun findByAccount(accountId: UUID, limit: Int, offset: Long): Flux<FinancialOperation>
    fun countByAccount(accountId: UUID): Mono<Long>
}
