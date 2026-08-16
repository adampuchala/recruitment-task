// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.financial.adapter.out

import com.adampuchala.bank.contracts.FinancialOperationType
import com.adampuchala.bank.financial.domain.AccountStatus
import com.adampuchala.bank.financial.domain.FinancialIdempotencyRecord
import com.adampuchala.bank.financial.domain.FinancialOperation
import com.adampuchala.bank.financial.domain.FinancialRepository
import com.adampuchala.bank.financial.domain.LockedAccount
import io.r2dbc.spi.Row
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitOne
import org.springframework.r2dbc.core.awaitOneOrNull
import org.springframework.r2dbc.core.awaitRowsUpdated
import org.springframework.stereotype.Repository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.intellij.lang.annotations.Language
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@Repository
class PostgresFinancialRepository(private val databaseClient: DatabaseClient) : FinancialRepository {
    override suspend fun tryInsertIdempotency(key: UUID, hash: String): Boolean = sql(
        """INSERT INTO financial_operations_idempotency_store
           (idempotency_key, request_hash, status, created_at)
           VALUES (:key, :hash, 'PENDING', now()) ON CONFLICT DO NOTHING""",
    ).bind("key", key).bind("hash", hash).fetch().awaitRowsUpdated() == 1L

    override suspend fun findIdempotency(key: UUID): FinancialIdempotencyRecord? = sql(
        """SELECT request_hash, response_payload::text AS response_payload, status
           FROM financial_operations_idempotency_store WHERE idempotency_key = :key""",
    ).bind("key", key).map { row, _ ->
        FinancialIdempotencyRecord(
            row.get("request_hash", String::class.java)!!,
            row.get("response_payload", String::class.java),
            row.get("status", String::class.java)!!,
        )
    }.awaitOneOrNull()

    override suspend fun completeSuccess(key: UUID, operationId: UUID, payload: String) {
        requireOneRowUpdated(sql(
        """UPDATE financial_operations_idempotency_store
           SET operation_id = :operationId, response_payload = CAST(:payload AS jsonb), status = 'SUCCESS'
           WHERE idempotency_key = :key""",
        ).bind("operationId", operationId).bind("payload", payload).bind("key", key).fetch().awaitRowsUpdated(), "complete financial idempotency")
    }

    override suspend fun completeError(key: UUID, payload: String) {
        requireOneRowUpdated(sql(
        """UPDATE financial_operations_idempotency_store
           SET response_payload = CAST(:payload AS jsonb), status = 'ERROR'
           WHERE idempotency_key = :key""",
        ).bind("payload", payload).bind("key", key).fetch().awaitRowsUpdated(), "complete financial idempotency error")
    }

    override suspend fun lockAccount(accountId: UUID): LockedAccount? = sql(
        """SELECT account_id, balance, status, version FROM user_accounts
           WHERE account_id = :accountId FOR UPDATE""",
    ).bind("accountId", accountId).map { row, _ ->
        LockedAccount(
            row.get("account_id", UUID::class.java)!!,
            row.get("balance", BigDecimal::class.java)!!,
            AccountStatus.valueOf(row.get("status", String::class.java)!!),
            row.get("version", Long::class.javaObjectType)!!,
        )
    }.awaitOneOrNull()

    override suspend fun updateBalance(accountId: UUID, balance: BigDecimal, updatedAt: Instant) {
        requireOneRowUpdated(sql(
        """UPDATE user_accounts SET balance = :balance, version = version + 1, updated_at = :updatedAt
           WHERE account_id = :accountId""",
    ).bind("balance", balance)
        .bind("updatedAt", OffsetDateTime.ofInstant(updatedAt, ZoneOffset.UTC))
        .bind("accountId", accountId).fetch().awaitRowsUpdated(), "update account balance")
    }

    override suspend fun insertOperation(operation: FinancialOperation) {
        var spec = sql(
            """INSERT INTO financial_operations
               (operation_id, type, from_account_id, to_account_id, amount, status, description, created_at)
               VALUES (:id, :type, :fromId, :toId, :amount, :status, :description, :createdAt)""",
        ).bind("id", operation.operationId)
            .bind("type", operation.type.name)
            .bind("amount", operation.amount)
            .bind("status", operation.status)
            .bind("createdAt", OffsetDateTime.ofInstant(operation.createdAt, ZoneOffset.UTC))
        spec = spec.bindNullable("fromId", operation.fromAccountId, UUID::class.java)
            .bindNullable("toId", operation.toAccountId, UUID::class.java)
            .bindNullable("description", operation.description, String::class.java)
        spec.fetch().awaitRowsUpdated()
    }

    override suspend fun insertOutbox(eventId: UUID, operationId: UUID, payload: String, createdAt: Instant) {
        sql(
        """INSERT INTO financial_operation_result_outbox
           (event_id, operation_id, payload, status, attempts, created_at)
           VALUES (:eventId, :operationId, CAST(:payload AS jsonb), 'PENDING', 0, :createdAt)""",
    ).bind("eventId", eventId).bind("operationId", operationId).bind("payload", payload)
        .bind("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC)).fetch().awaitRowsUpdated()
    }

    override suspend fun findOperation(operationId: UUID): FinancialOperation? = sql(
        """SELECT operation_id, type, from_account_id, to_account_id, amount, status, description, created_at
           FROM financial_operations WHERE operation_id = :operationId""",
    ).bind("operationId", operationId).map(::mapOperation).awaitOneOrNull()

    override suspend fun findByAccount(accountId: UUID, limit: Int, offset: Long): List<FinancialOperation> = sql(
        """SELECT operation_id, type, from_account_id, to_account_id, amount, status, description, created_at
           FROM financial_operations
           WHERE from_account_id = :accountId OR to_account_id = :accountId
           ORDER BY created_at DESC, operation_id DESC LIMIT :limit OFFSET :offset""",
    ).bind("accountId", accountId).bind("limit", limit).bind("offset", offset).map(::mapOperation).all().asFlow().toList()

    override suspend fun countByAccount(accountId: UUID): Long = sql(
        "SELECT COUNT(*) AS count FROM financial_operations WHERE from_account_id = :accountId OR to_account_id = :accountId",
    ).bind("accountId", accountId).map { row, _ -> row.get("count", Long::class.javaObjectType)!! }.awaitOne()

    private fun sql(@Language("PostgreSQL") query: String) = databaseClient.sql(query)

    private fun requireOneRowUpdated(rowsUpdated: Long, operation: String) {
        check(rowsUpdated == 1L) { "Expected one row to be updated while attempting to $operation, but updated $rowsUpdated" }
    }

    private fun mapOperation(row: Row, metadata: io.r2dbc.spi.RowMetadata) = FinancialOperation(
        row.get("operation_id", UUID::class.java)!!,
        FinancialOperationType.valueOf(row.get("type", String::class.java)!!),
        row.get("from_account_id", UUID::class.java),
        row.get("to_account_id", UUID::class.java),
        row.get("amount", BigDecimal::class.java)!!,
        row.get("status", String::class.java)!!,
        row.get("description", String::class.java),
        row.get("created_at", OffsetDateTime::class.java)!!.toInstant(),
    )

    private fun <T : Any> DatabaseClient.GenericExecuteSpec.bindNullable(name: String, value: T?, type: Class<T>): DatabaseClient.GenericExecuteSpec =
        if (value == null) bindNull(name, type) else bind(name, value)
}
