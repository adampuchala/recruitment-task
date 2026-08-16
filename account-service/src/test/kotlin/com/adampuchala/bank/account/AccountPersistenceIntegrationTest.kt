// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.account

import com.adampuchala.bank.account.adapter.out.PostgresAccountRepository
import com.adampuchala.bank.account.application.AccountApplicationService
import com.adampuchala.bank.account.application.IdempotencyConflictException
import com.adampuchala.bank.account.domain.AccountStatus
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.r2dbc.spi.ConnectionFactories
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
import org.springframework.transaction.reactive.TransactionalOperator
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.springframework.r2dbc.core.awaitRowsUpdated
import java.io.File
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountPersistenceIntegrationTest {
    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer("postgres:16.14")
    }

    private lateinit var databaseClient: DatabaseClient
    private lateinit var service: AccountApplicationService

    @BeforeAll
    fun initializeDatabase() {
        migrate()
        val connectionFactory = ConnectionFactories.get(
            "r2dbc:postgresql://${postgres.username}:${postgres.password}@${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}",
        )
        databaseClient = DatabaseClient.create(connectionFactory)
        val repository = PostgresAccountRepository(databaseClient)
        val transaction = TransactionalOperator.create(R2dbcTransactionManager(connectionFactory))
        val objectMapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
        service = AccountApplicationService(repository, objectMapper, transaction)
    }

    @BeforeEach
    fun cleanDatabase() = runBlocking {
        databaseClient.sql(
            """TRUNCATE financial_operations_audit_events, financial_operation_result_outbox,
               financial_operations_idempotency_store, financial_operations,
               create_account_idempotency_store, user_accounts CASCADE""",
        ).fetch().awaitRowsUpdated()
        Unit
    }

    @Test
    fun `should create retrieve and change status`() = runBlocking {
        val created = service.create(UUID.randomUUID(), "Ada", "Lovelace")
        val blocked = service.changeStatus(created.account.accountId, AccountStatus.BLOCKED)
        val retrieved = service.get(created.account.accountId)

        assertEquals(AccountStatus.BLOCKED, blocked.status)
        assertEquals(blocked, retrieved)
        assertEquals(1L, count("user_accounts"))
    }

    @Test
    fun `should create one account for concurrent repeated idempotency key`() = runBlocking {
        val key = UUID.randomUUID()

        val results = coroutineScope {
            (0 until 8).map { async { service.create(key, "Grace", "Hopper") } }.awaitAll()
        }

        assertEquals(8, results.size)
        assertEquals(1, results.count { !it.replay })
        assertEquals(1, results.map { it.account.accountId }.distinct().size)
        assertEquals(1L, count("user_accounts"))
        assertEquals(1L, count("create_account_idempotency_store"))
    }

    @Test
    fun `should reject same idempotency key with different account request`() = runBlocking {
        val key = UUID.randomUUID()
        service.create(key, "First", "Person")

        assertFailsWith<IdempotencyConflictException> {
            service.create(key, "Second", "Person")
        }

        assertEquals(1L, count("user_accounts"))
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

    private suspend fun count(table: String): Long = databaseClient.sql("SELECT COUNT(*) AS count FROM $table")
        .map { row, _ -> row.get("count", java.lang.Long::class.java)!!.toLong() }
        .one().awaitSingle()
}
