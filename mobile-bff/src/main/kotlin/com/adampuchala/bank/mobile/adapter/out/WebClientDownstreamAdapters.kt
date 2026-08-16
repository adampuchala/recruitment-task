// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.mobile.adapter.out

import com.adampuchala.bank.mobile.application.AccountServicePort
import com.adampuchala.bank.mobile.application.FinancialOperationsPort
import com.adampuchala.bank.mobile.domain.AccountStatus
import com.adampuchala.bank.mobile.domain.CreateMobileAccountCommand
import com.adampuchala.bank.mobile.domain.DepositFundsCommand
import com.adampuchala.bank.mobile.domain.DownstreamError
import com.adampuchala.bank.mobile.domain.DownstreamResult
import com.adampuchala.bank.mobile.domain.DownstreamUnavailableException
import com.adampuchala.bank.mobile.domain.MobileAccount
import com.adampuchala.bank.mobile.domain.MobileDeposit
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Component
class WebClientAccountServiceAdapter(
    @param:Qualifier("accountServiceWebClient") private val client: WebClient,
) : AccountServicePort {
    override suspend fun create(idempotencyKey: UUID, command: CreateMobileAccountCommand): DownstreamResult<MobileAccount> =
        exchange(
            client.post().uri("/api/v1/accounts")
                .header("Idempotency-Key", idempotencyKey.toString())
                .bodyValue(CreateAccountPayload(command.firstName, command.lastName)),
            AccountPayload::class.java,
            "Account Service",
        ).map { it.toMobileAccount() }

    override suspend fun get(accountId: UUID): DownstreamResult<MobileAccount> =
        exchange(client.get().uri("/api/v1/accounts/{accountId}", accountId), AccountPayload::class.java, "Account Service")
            .map { it.toMobileAccount() }
}

@Component
class WebClientFinancialOperationsAdapter(
    @param:Qualifier("financialOperationsWebClient") private val client: WebClient,
) : FinancialOperationsPort {
    override suspend fun deposit(idempotencyKey: UUID, accountId: UUID, command: DepositFundsCommand): DownstreamResult<MobileDeposit> =
        exchange(
            client.post().uri("/api/v1/accounts/{accountId}/deposits", accountId)
                .header("Idempotency-Key", idempotencyKey.toString())
                .bodyValue(DepositPayload(command.amount, command.description)),
            DepositOperationPayload::class.java,
            "Financial Operations Service",
        ).map { it.toMobileDeposit() }
}

private suspend fun <T : Any> exchange(
    request: WebClient.RequestHeadersSpec<*>,
    responseType: Class<T>,
    serviceName: String,
): DownstreamResult<T> = try {
    request.exchangeToMono { response ->
        if (response.statusCode().is2xxSuccessful) {
            response.bodyToMono(responseType)
                .map<DownstreamResult<T>> { DownstreamResult.Success(response.statusCode().value(), it) }
        } else {
            val statusCode = if (response.statusCode().is5xxServerError) HttpStatus.SERVICE_UNAVAILABLE.value() else response.statusCode().value()
            response.bodyToMono(DownstreamErrorPayload::class.java)
                .map<DownstreamResult<T>> {
                    DownstreamResult.Failure(
                        statusCode,
                        if (response.statusCode().is5xxServerError) DownstreamError("DOWNSTREAM_UNAVAILABLE", "A required backend service is unavailable") else DownstreamError(it.code, it.message),
                    )
                }
                .defaultIfEmpty(
                    DownstreamResult.Failure(
                        statusCode,
                        DownstreamError(
                            if (response.statusCode().is5xxServerError) "DOWNSTREAM_UNAVAILABLE" else "DOWNSTREAM_ERROR",
                            if (response.statusCode().is5xxServerError) "A required backend service is unavailable" else "Downstream request failed",
                        ),
                    ),
                )
        }
    }.awaitSingle()
} catch (exception: Exception) {
    throw DownstreamUnavailableException(serviceName, exception)
}

private fun <T, R> DownstreamResult<T>.map(transform: (T) -> R): DownstreamResult<R> = when (this) {
    is DownstreamResult.Success -> DownstreamResult.Success(statusCode, transform(body))
    is DownstreamResult.Failure -> this
}

private data class CreateAccountPayload(val firstName: String, val lastName: String)
private data class DepositPayload(val amount: BigDecimal, val description: String?)
private data class DownstreamErrorPayload(val code: String, val message: String)
private data class AccountPayload(val accountId: UUID, val firstName: String, val lastName: String, val balance: BigDecimal, val status: AccountStatus, val createdAt: Instant, val updatedAt: Instant)
private data class DepositOperationPayload(val operationId: UUID, val type: String, val status: String, val fromAccountId: UUID?, val toAccountId: UUID?, val amount: BigDecimal, val balanceAfter: BigDecimal?, val description: String?, val createdAt: Instant)

private fun AccountPayload.toMobileAccount() = MobileAccount(accountId, firstName, lastName, balance, status, createdAt, updatedAt)
private fun DepositOperationPayload.toMobileDeposit() = MobileDeposit(operationId, type, status, fromAccountId, requireNotNull(toAccountId), amount, requireNotNull(balanceAfter), description, createdAt)
