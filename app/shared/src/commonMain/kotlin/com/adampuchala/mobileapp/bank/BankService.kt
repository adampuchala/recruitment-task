// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.mobileapp.bank

import com.adampuchala.mobileapp.api.model.AccountResponse
import com.adampuchala.mobileapp.api.model.DepositResponse
import com.adampuchala.mobileapp.network.BankApiClient
import com.adampuchala.mobileapp.network.BankApiResult
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Inject
@SingleIn(AppScope::class)
class BankService(private val apiClient: BankApiClient) {
    suspend fun createAccount(firstName: String, lastName: String): BankApiResult<AccountResponse> {
        val normalizedFirstName = firstName.trim()
        val normalizedLastName = lastName.trim()
        if (normalizedFirstName.isBlank() || normalizedLastName.isBlank()) {
            return BankApiResult.Failure("First name and last name are required.")
        }
        if (normalizedFirstName.length > 100 || normalizedLastName.length > 100) {
            return BankApiResult.Failure("Names cannot be longer than 100 characters.")
        }
        return apiClient.createAccount(normalizedFirstName, normalizedLastName, newIdempotencyKey())
    }

    suspend fun getAccount(accountId: String): BankApiResult<AccountResponse> {
        if (accountId.isBlank()) return BankApiResult.Failure("Enter an account ID.")
        return apiClient.getAccount(accountId.trim())
    }

    suspend fun deposit(accountId: String, amountInput: String, description: String): BankApiResult<DepositResponse> {
        val normalizedAccountId = accountId.trim()
        if (normalizedAccountId.isBlank()) return BankApiResult.Failure("Enter an account ID.")
        if (!amountInput.matches(AMOUNT_PATTERN)) {
            return BankApiResult.Failure("Amount must be positive and have at most four decimal places.")
        }
        val amount = amountInput.toDoubleOrNull()
            ?: return BankApiResult.Failure("Enter a valid amount.")
        if (!amount.isFinite() || amount <= 0.0) {
            return BankApiResult.Failure("Amount must be greater than zero.")
        }
        if (description.length > 500) return BankApiResult.Failure("Description cannot be longer than 500 characters.")
        return apiClient.deposit(normalizedAccountId, amount, description.trim().ifBlank { null }, newIdempotencyKey())
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newIdempotencyKey(): String = Uuid.random().toString()

    private companion object {
        val AMOUNT_PATTERN = Regex("^(?:0|[1-9]\\d{0,14})(?:\\.\\d{1,4})?$")
    }
}
