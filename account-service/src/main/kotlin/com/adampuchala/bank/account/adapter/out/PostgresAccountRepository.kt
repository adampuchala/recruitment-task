// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.account.adapter.out

import com.adampuchala.bank.account.domain.Account
import com.adampuchala.bank.account.domain.AccountIdempotencyRecord
import com.adampuchala.bank.account.domain.AccountRepository
import com.adampuchala.bank.account.domain.AccountStatus
import org.intellij.lang.annotations.Language
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitOneOrNull
import org.springframework.r2dbc.core.awaitRowsUpdated
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class PostgresAccountRepository(private val databaseClient: DatabaseClient) : AccountRepository {
    override suspend fun findById(accountId: UUID): Account? = accountQuery(false, accountId)

    override suspend fun findByIdForUpdate(accountId: UUID): Account? = accountQuery(true, accountId)

    private suspend fun accountQuery(lock: Boolean, accountId: UUID): Account? {
        val suffix = if (lock) " FOR UPDATE" else ""
        return databaseClient.sql(
            """SELECT account_id, first_name, last_name, balance, status, version, created_at, updated_at
               FROM user_accounts WHERE account_id = :accountId$suffix""",
        ).bind("accountId", accountId).map { row, _ ->
            Account(
                accountId = row.get("account_id", UUID::class.java)!!,
                firstName = row.get("first_name", String::class.java)!!,
                lastName = row.get("last_name", String::class.java)!!,
                balance = row.get("balance", java.math.BigDecimal::class.java)!!,
                status = AccountStatus.valueOf(row.get("status", String::class.java)!!),
                version = row.get("version", Long::class.javaObjectType)!!,
                createdAt = row.instant("created_at"),
                updatedAt = row.instant("updated_at"),
            )
        }.awaitOneOrNull()
    }

    override suspend fun insert(account: Account) {
        sql(
        """INSERT INTO user_accounts
           (account_id, first_name, last_name, balance, status, version, created_at, updated_at)
           VALUES (:id, :firstName, :lastName, :balance, :status, :version, :createdAt, :updatedAt)""",
    ).bind("id", account.accountId)
        .bind("firstName", account.firstName)
        .bind("lastName", account.lastName)
        .bind("balance", account.balance)
        .bind("status", account.status.name)
        .bind("version", account.version)
        .bind("createdAt", OffsetDateTime.ofInstant(account.createdAt, ZoneOffset.UTC))
        .bind("updatedAt", OffsetDateTime.ofInstant(account.updatedAt, ZoneOffset.UTC))
        .fetch().awaitRowsUpdated()
    }

    override suspend fun updateStatus(accountId: UUID, status: AccountStatus, updatedAt: Instant) {
        requireOneRowUpdated(sql(
        """UPDATE user_accounts SET status = :status, version = version + 1, updated_at = :updatedAt
           WHERE account_id = :accountId""",
    ).bind("status", status.name)
        .bind("updatedAt", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC))
        .bind("accountId", accountId)
        .fetch().awaitRowsUpdated(), "update account status")
    }

    override suspend fun tryInsertIdempotency(key: UUID, requestHash: String): Boolean = sql(
        """INSERT INTO create_account_idempotency_store
           (idempotency_key, request_hash, status, created_at)
           VALUES (:key, :hash, 'PENDING', now()) ON CONFLICT DO NOTHING""",
    ).bind("key", key).bind("hash", requestHash).fetch().awaitRowsUpdated() == 1L

    override suspend fun findIdempotency(key: UUID): AccountIdempotencyRecord? = sql(
        """SELECT idempotency_key, account_id, request_hash, response_payload::text AS response_payload, status
           FROM create_account_idempotency_store WHERE idempotency_key = :key""",
    ).bind("key", key).map { row, _ ->
        AccountIdempotencyRecord(
            idempotencyKey = row.get("idempotency_key", UUID::class.java)!!,
            accountId = row.get("account_id", UUID::class.java),
            requestHash = row.get("request_hash", String::class.java)!!,
            responsePayload = row.get("response_payload", String::class.java),
            status = row.get("status", String::class.java)!!,
        )
    }.awaitOneOrNull()

    override suspend fun completeIdempotency(key: UUID, accountId: UUID, responsePayload: String) {
        requireOneRowUpdated(sql(
        """UPDATE create_account_idempotency_store
           SET account_id = :accountId, response_payload = CAST(:payload AS jsonb), status = 'SUCCESS'
           WHERE idempotency_key = :key""",
        ).bind("accountId", accountId).bind("payload", responsePayload).bind("key", key).fetch().awaitRowsUpdated(), "complete account idempotency")
    }

    private fun io.r2dbc.spi.Row.instant(column: String): Instant =
        get(column, OffsetDateTime::class.java)!!.toInstant()

    private fun sql(@Language("PostgreSQL") query: String) = databaseClient.sql(query)

    private fun requireOneRowUpdated(rowsUpdated: Long, operation: String) {
        check(rowsUpdated == 1L) { "Expected one row to be updated while attempting to $operation, but updated $rowsUpdated" }
    }
}
