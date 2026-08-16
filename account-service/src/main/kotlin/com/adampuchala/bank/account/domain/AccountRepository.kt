// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.account.domain

import reactor.core.publisher.Mono
import java.util.UUID

interface AccountRepository {
    fun findById(accountId: UUID): Mono<Account>
    fun findByIdForUpdate(accountId: UUID): Mono<Account>
    fun insert(account: Account): Mono<Void>
    fun updateStatus(accountId: UUID, status: AccountStatus, updatedAt: java.time.Instant): Mono<Void>
    fun tryInsertIdempotency(key: UUID, requestHash: String): Mono<Boolean>
    fun findIdempotency(key: UUID): Mono<AccountIdempotencyRecord>
    fun completeIdempotency(key: UUID, accountId: UUID, responsePayload: String): Mono<Void>
}
