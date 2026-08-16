// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.financial

import com.adampuchala.bank.financial.adapter.out.PostgresFinancialRepository
import com.adampuchala.bank.financial.application.CommandResult
import com.adampuchala.bank.financial.application.FinancialApplicationService
import com.adampuchala.bank.financial.domain.FinancialRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.r2dbc.spi.ConnectionFactories
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.FileSystemResourceAccessor
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitOne
import org.springframework.r2dbc.core.awaitRowsUpdated
import org.springframework.transaction.reactive.TransactionalOperator
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import java.math.BigDecimal
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FinancialConsistencyIntegrationTest {
    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer("postgres:16.14")
    }

    private lateinit var databaseClient: DatabaseClient
    private lateinit var repository: PostgresFinancialRepository
    private lateinit var transactionalOperator: TransactionalOperator
    private lateinit var objectMapper: ObjectMapper
    private lateinit var service: FinancialApplicationService

    @BeforeAll
    fun initializeDatabase() {
        migrate()
        val connectionFactory = ConnectionFactories.get(
            "r2dbc:postgresql://${postgres.username}:${postgres.password}@${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}",
        )
        databaseClient = DatabaseClient.create(connectionFactory)
        repository = PostgresFinancialRepository(databaseClient)
        transactionalOperator = TransactionalOperator.create(R2dbcTransactionManager(connectionFactory))
        objectMapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
        service = FinancialApplicationService(repository, objectMapper, transactionalOperator)
    }

    @BeforeEach
    fun prepareDatabase() = runBlocking {
        databaseClient.sql(
            """TRUNCATE financial_operations_audit_events, financial_operation_result_outbox,
               financial_operations_idempotency_store, financial_operations,
               create_account_idempotency_store, user_accounts CASCADE""",
        ).fetch().awaitRowsUpdated()
        Unit
    }

    @Test
    fun `should update balance history idempotency and outbox atomically`() = runBlocking {
        val accountId = insertAccount(BigDecimal("100.0000"))
        val key = UUID.randomUUID()

        val first = service.withdrawal(key, accountId, BigDecimal("25.0000"), "cash")
        val replay = service.withdrawal(key, accountId, BigDecimal("25.0000"), "cash")

        assertIs<CommandResult.Accepted>(first)
        assertIs<CommandResult.Accepted>(replay)
        assertEquals(true, replay.replay)
        assertEquals(BigDecimal("75.0000"), balance(accountId))
        assertEquals(1L, count("financial_operations"))
        assertEquals(1L, count("financial_operation_result_outbox"))
        assertEquals(1L, count("financial_operations_idempotency_store"))
    }

    @Test
    fun `should prevent negative balance during concurrent withdrawals`() = runBlocking {
        val accountId = insertAccount(BigDecimal("100.0000"))

        val results = coroutineScope {
            (0 until 20).map {
                async { service.withdrawal(UUID.randomUUID(), accountId, BigDecimal("10.0000"), null) }
            }.awaitAll()
        }

        assertEquals(10, results.count { it is CommandResult.Accepted })
        assertEquals(10, results.count { it is CommandResult.Rejected })
        assertEquals(BigDecimal("0.0000"), balance(accountId))
        assertEquals(10L, count("financial_operations"))
        assertEquals(10L, count("financial_operation_result_outbox"))
    }

    @Test
    fun `should serialize concurrent repeated idempotency key`() = runBlocking {
        val accountId = insertAccount(BigDecimal.ZERO.setScale(4))
        val key = UUID.randomUUID()

        val results = coroutineScope {
            (0 until 8).map {
                async { service.deposit(key, accountId, BigDecimal("5.0000"), "same request") }
            }.awaitAll()
        }

        assertEquals(8, results.size)
        assertEquals(1, results.count { it is CommandResult.Accepted && !it.replay })
        assertEquals(BigDecimal("5.0000"), balance(accountId))
        assertEquals(1L, count("financial_operations"))
        assertEquals(1L, count("financial_operation_result_outbox"))
    }

    @Test
    fun `should avoid deadlock and preserve total during opposite concurrent transfers`() = runBlocking {
        val firstAccount = insertAccount(BigDecimal("100.0000"))
        val secondAccount = insertAccount(BigDecimal("100.0000"))

        val results = coroutineScope {
            (0 until 10).flatMap {
                listOf(
                    async { service.transfer(UUID.randomUUID(), firstAccount, secondAccount, BigDecimal("1.0000"), null) },
                    async { service.transfer(UUID.randomUUID(), secondAccount, firstAccount, BigDecimal("1.0000"), null) },
                )
            }.awaitAll()
        }

        assertEquals(20, results.count { it is CommandResult.Accepted })
        assertEquals(BigDecimal("100.0000"), balance(firstAccount))
        assertEquals(BigDecimal("100.0000"), balance(secondAccount))
        assertEquals(20L, count("financial_operations"))
    }

    @Test
    fun `should roll back balance operation idempotency and outbox on atomic failure`() = runBlocking {
        val accountId = insertAccount(BigDecimal("50.0000"))
        val failingRepository = FailingOutboxRepository(repository)
        val failingService = FinancialApplicationService(failingRepository, objectMapper, transactionalOperator)

        val exception = assertFailsWith<IllegalStateException> {
            failingService.deposit(UUID.randomUUID(), accountId, BigDecimal("10.0000"), null)
        }
        assertEquals("simulated outbox failure", exception.message)

        assertEquals(BigDecimal("50.0000"), balance(accountId))
        assertEquals(0L, count("financial_operations"))
        assertEquals(0L, count("financial_operation_result_outbox"))
        assertEquals(0L, count("financial_operations_idempotency_store"))
    }

    private fun migrate() {
        val repositoryRoot = File(System.getProperty("repo.root", "..")).canonicalFile
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            val database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(JdbcConnection(connection))
            FileSystemResourceAccessor(repositoryRoot).use { accessor ->
                Liquibase(
                    "infrastructure/db-migrations/changelog/db.changelog-master.yaml",
                    accessor,
                    database,
                ).use { it.update() }
            }
        }
    }

    private suspend fun insertAccount(initialBalance: BigDecimal): UUID {
        val accountId = UUID.randomUUID()
        databaseClient.sql(
            """INSERT INTO user_accounts
               (account_id, first_name, last_name, balance, status, version, created_at, updated_at)
               VALUES (:id, 'Test', 'Account', :balance, 'ACTIVE', 0, now(), now())""",
        ).bind("id", accountId).bind("balance", initialBalance).fetch().awaitRowsUpdated()
        return accountId
    }

    private suspend fun balance(accountId: UUID): BigDecimal = databaseClient.sql(
        "SELECT balance FROM user_accounts WHERE account_id = :id",
    ).bind("id", accountId).map { row, _ -> row.get("balance", BigDecimal::class.java)!! }.awaitOne()

    private suspend fun count(table: String): Long = databaseClient.sql("SELECT COUNT(*) AS count FROM $table")
        .map { row, _ -> row.get("count", java.lang.Long::class.java)!!.toLong() }.awaitOne()

    private class FailingOutboxRepository(private val delegate: FinancialRepository) : FinancialRepository by delegate {
        override suspend fun insertOutbox(
            eventId: UUID,
            operationId: UUID,
            payload: String,
            createdAt: Instant,
        ) {
            throw IllegalStateException("simulated outbox failure")
        }
    }
}
