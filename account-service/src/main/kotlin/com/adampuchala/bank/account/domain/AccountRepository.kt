// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.account.domain

import java.time.Instant
import java.util.UUID

interface AccountRepository {
    suspend fun findById(accountId: UUID): Account?
    suspend fun findByIdForUpdate(accountId: UUID): Account?
    suspend fun insert(account: Account)
    suspend fun updateStatus(accountId: UUID, status: AccountStatus, updatedAt: Instant)
    suspend fun tryInsertIdempotency(key: UUID, requestHash: String): Boolean
    suspend fun findIdempotency(key: UUID): AccountIdempotencyRecord?
    suspend fun completeIdempotency(key: UUID, accountId: UUID, responsePayload: String)
}
