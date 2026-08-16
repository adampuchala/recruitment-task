// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.mobileapp.network

import com.adampuchala.mobileapp.api.model.AccountResponse
import com.adampuchala.mobileapp.api.model.ApiError
import com.adampuchala.mobileapp.api.model.CreateAccountRequest
import com.adampuchala.mobileapp.api.model.DepositRequest
import com.adampuchala.mobileapp.api.model.DepositResponse
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException

sealed interface BankApiResult<out T> {
    data class Success<T>(val value: T) : BankApiResult<T>
    data class Failure(val message: String) : BankApiResult<Nothing>
}

@Inject
@SingleIn(AppScope::class)
class BankApiClient(private val client: HttpClient) {
    suspend fun createAccount(firstName: String, lastName: String, idempotencyKey: String): BankApiResult<AccountResponse> =
        execute {
            client.post("api/v1/mobile/accounts") {
                header("Idempotency-Key", idempotencyKey)
                contentType(ContentType.Application.Json)
                setBody(CreateAccountRequest(firstName = firstName, lastName = lastName))
            }
        }

    suspend fun getAccount(accountId: String): BankApiResult<AccountResponse> =
        execute { client.get("api/v1/mobile/accounts/$accountId") }

    suspend fun deposit(
        accountId: String,
        amount: Double,
        description: String?,
        idempotencyKey: String,
    ): BankApiResult<DepositResponse> = execute {
        client.post("api/v1/mobile/accounts/$accountId/deposits") {
            header("Idempotency-Key", idempotencyKey)
            contentType(ContentType.Application.Json)
            setBody(DepositRequest(amount = amount, description = description))
        }
    }

    private suspend inline fun <reified T> execute(call: suspend () -> HttpResponse): BankApiResult<T> = try {
        val response = call()
        if (response.status.isSuccess()) {
            BankApiResult.Success(response.body())
        } else {
            val error = runCatching { response.body<ApiError>() }.getOrNull()
            BankApiResult.Failure(error?.message ?: "Request failed (${response.status.value})")
        }
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: ResponseException) {
        val error = runCatching { exception.response.body<ApiError>() }.getOrNull()
        BankApiResult.Failure(error?.message ?: "Request failed (${exception.response.status.value})")
    } catch (_: Exception) {
        BankApiResult.Failure("Unable to reach the banking service. Please try again.")
    }
}
