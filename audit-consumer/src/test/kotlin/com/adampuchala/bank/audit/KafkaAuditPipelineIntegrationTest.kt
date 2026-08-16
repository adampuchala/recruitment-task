// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.audit

import com.adampuchala.bank.audit.adapter.out.PostgresAuditRepository
import com.adampuchala.bank.audit.application.AuditEventConsumer
import com.adampuchala.bank.audit.application.TransactionalAuditEventHandler
import com.adampuchala.bank.contracts.FinancialOperationCompleted
import com.adampuchala.bank.contracts.FinancialOperationType
import com.adampuchala.bank.outbox.adapter.out.PostgresOutboxRepository
import com.adampuchala.bank.outbox.application.OutboxPublisher
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.r2dbc.spi.ConnectionFactories
import kotlinx.coroutines.runBlocking
import liquibase.Liquibase
import liquibase.database.DatabaseFactory
import liquibase.database.jvm.JdbcConnection
import liquibase.resource.FileSystemResourceAccessor
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.r2dbc.connection.R2dbcTransactionManager
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitOne
import org.springframework.r2dbc.core.awaitRowsUpdated
import org.springframework.transaction.reactive.TransactionalOperator
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.math.BigDecimal
import java.sql.DriverManager
import java.time.Duration
import java.time.Instant
import java.util.Properties
import java.util.UUID
import kotlin.test.assertEquals

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaAuditPipelineIntegrationTest {
    companion object {
        private const val KAFKA_PORT = 19093

        @Container
        @JvmField
        val postgres = PostgreSQLContainer("postgres:16.14")

        @Container
        @JvmField
        val redpanda = FixedPortRedpandaContainer(KAFKA_PORT)
    }

    private lateinit var databaseClient: DatabaseClient
    private lateinit var objectMapper: ObjectMapper
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>
    private lateinit var auditConsumer: AuditEventConsumer

    @BeforeAll
    fun initializeInfrastructure() {
        migrate()
        val connectionFactory = ConnectionFactories.get(
            "r2dbc:postgresql://${postgres.username}:${postgres.password}@${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}",
        )
        databaseClient = DatabaseClient.create(connectionFactory)
        val transaction = TransactionalOperator.create(R2dbcTransactionManager(connectionFactory))
        objectMapper = ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .registerModule(JavaTimeModule())
        kafkaTemplate = KafkaTemplate(DefaultKafkaProducerFactory(producerProperties()))
        auditConsumer = AuditEventConsumer(
            TransactionalAuditEventHandler(PostgresAuditRepository(databaseClient), transaction),
            objectMapper,
        )

        AdminClient.create(mapOf(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:$KAFKA_PORT")).use { admin ->
            admin.createTopics(listOf(NewTopic("financial-operations", 1, 1))).all().get()
        }
    }

    @Test
    fun `should publish outbox through Redpanda and deduplicate audit replay`() = runBlocking {
        val event = event()
        seedOutbox(event)
        val kafkaConsumer = KafkaConsumer<String, String>(consumerProperties())

        kafkaConsumer.use { consumer ->
            consumer.subscribe(listOf("financial-operations"))
            consumer.poll(Duration.ofSeconds(1))

            val outboxPublisher = OutboxPublisher(
                PostgresOutboxRepository(databaseClient),
                kafkaTemplate,
                "financial-operations",
                50,
            )
            outboxPublisher.publishPending()

            val firstPayload = receiveOne(consumer)
            auditConsumer.consume(firstPayload)
            kafkaTemplate.send("financial-operations", event.operationId.toString(), firstPayload).get()
            val duplicatePayload = receiveOne(consumer)
            auditConsumer.consume(duplicatePayload)
        }

        assertEquals("PROCESSED", stringValue("SELECT status FROM financial_operation_result_outbox WHERE event_id = '${event.eventId}'"))
        assertEquals(1L, longValue("SELECT count(*) FROM financial_operations_audit_events WHERE event_id = '${event.eventId}'"))
    }

    private fun receiveOne(consumer: KafkaConsumer<String, String>): String {
        val deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos()
        while (System.nanoTime() < deadline) {
            val record = consumer.poll(Duration.ofMillis(500)).firstOrNull()
            if (record != null) return record.value()
        }
        error("Kafka event was not received before timeout")
    }

    private suspend fun seedOutbox(event: FinancialOperationCompleted) {
        val accountId = event.toAccountId!!
        databaseClient.sql(
            """INSERT INTO user_accounts
               (account_id, first_name, last_name, balance, status, version, created_at, updated_at)
               VALUES (:accountId, 'Integration', 'Test', :amount, 'ACTIVE', 1, now(), now())""",
        ).bind("accountId", accountId).bind("amount", event.amount).fetch().awaitRowsUpdated()
        databaseClient.sql(
            """INSERT INTO financial_operations
               (operation_id, type, from_account_id, to_account_id, amount, status, created_at)
               VALUES (:operationId, 'DEPOSIT', NULL, :accountId, :amount, 'SUCCESS', now())""",
        ).bind("operationId", event.operationId).bind("accountId", accountId).bind("amount", event.amount)
            .fetch().awaitRowsUpdated()
        databaseClient.sql(
            """INSERT INTO financial_operation_result_outbox
               (event_id, operation_id, payload, status, attempts, created_at)
               VALUES (:eventId, :operationId, CAST(:payload AS jsonb), 'PENDING', 0, now())""",
        ).bind("eventId", event.eventId).bind("operationId", event.operationId)
            .bind("payload", objectMapper.writeValueAsString(event)).fetch().awaitRowsUpdated()
    }

    private fun event() = FinancialOperationCompleted(
        eventId = UUID.randomUUID(),
        operationId = UUID.randomUUID(),
        operationType = FinancialOperationType.DEPOSIT,
        fromAccountId = null,
        toAccountId = UUID.randomUUID(),
        amount = BigDecimal("42.5000"),
        occurredAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

    private fun producerProperties() = mapOf<String, Any>(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to "localhost:$KAFKA_PORT",
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
        ProducerConfig.ACKS_CONFIG to "all",
    )

    private fun consumerProperties() = Properties().apply {
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:$KAFKA_PORT")
        put(ConsumerConfig.GROUP_ID_CONFIG, "audit-pipeline-${UUID.randomUUID()}")
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java)
    }

    private suspend fun stringValue(sql: String): String = databaseClient.sql(sql)
        .map { row, _ -> row.get(0, String::class.java)!! }.awaitOne()

    private suspend fun longValue(sql: String): Long = databaseClient.sql(sql)
        .map { row, _ -> row.get(0, Long::class.javaObjectType)!! }.awaitOne()

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

    class FixedPortRedpandaContainer(hostPort: Int) :
        GenericContainer<FixedPortRedpandaContainer>(
            DockerImageName.parse("docker.redpanda.com/redpandadata/redpanda:v25.1.9"),
        ) {
        init {
            addFixedExposedPort(hostPort, 9092)
            withCommand(
                "redpanda", "start",
                "--overprovisioned", "--smp=1", "--memory=512M", "--reserve-memory=0M",
                "--node-id=0", "--check=false",
                "--kafka-addr=0.0.0.0:9092",
                "--advertise-kafka-addr=localhost:$hostPort",
            )
            waitingFor(Wait.forLogMessage(".*Successfully started Redpanda!.*\\n", 1))
        }
    }
}
