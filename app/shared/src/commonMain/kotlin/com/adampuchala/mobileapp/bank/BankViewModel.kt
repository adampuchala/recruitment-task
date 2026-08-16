// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.mobileapp.bank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.adampuchala.mobileapp.api.model.AccountResponse
import com.adampuchala.mobileapp.network.BankApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BankUiState(
    val firstName: String = "",
    val lastName: String = "",
    val accountId: String = "",
    val amount: String = "",
    val description: String = "",
    val account: AccountResponse? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

class BankViewModel(private val bankService: BankService) : ViewModel() {
    private val _uiState = MutableStateFlow(BankUiState())
    val uiState = _uiState.asStateFlow()

    fun updateFirstName(value: String) = update { copy(firstName = value) }
    fun updateLastName(value: String) = update { copy(lastName = value) }
    fun updateAccountId(value: String) = update { copy(accountId = value) }
    fun updateAmount(value: String) = update { copy(amount = value) }
    fun updateDescription(value: String) = update { copy(description = value) }
    fun clearFeedback() = update { copy(message = null, error = null) }

    fun createAccount() = viewModelScope.launch {
        val current = startRequest()
        when (val result = bankService.createAccount(current.firstName, current.lastName)) {
            is BankApiResult.Success -> update {
                copy(account = result.value, accountId = result.value.accountId, isLoading = false, message = "Account created.")
            }
            is BankApiResult.Failure -> finishWithError(result.message)
        }
    }

    fun loadAccount() = viewModelScope.launch {
        val current = startRequest()
        when (val result = bankService.getAccount(current.accountId)) {
            is BankApiResult.Success -> update { copy(account = result.value, isLoading = false, message = "Account loaded.") }
            is BankApiResult.Failure -> finishWithError(result.message)
        }
    }

    fun deposit() = viewModelScope.launch {
        val current = startRequest()
        when (val result = bankService.deposit(current.accountId, current.amount, current.description)) {
            is BankApiResult.Success -> update {
                copy(
                    amount = "",
                    description = "",
                    account = account?.takeIf { it.accountId == current.accountId }?.copy(
                        balance = result.value.balanceAfter,
                        updatedAt = result.value.createdAt,
                    ),
                    isLoading = false,
                    message = "Deposit completed. Balance: ${result.value.balanceAfter}",
                )
            }
            is BankApiResult.Failure -> finishWithError(result.message)
        }
    }

    private fun startRequest(): BankUiState {
        val current = _uiState.value
        update { copy(isLoading = true, message = null, error = null) }
        return current
    }

    private fun finishWithError(message: String) = update { copy(isLoading = false, error = message) }

    private fun update(transform: BankUiState.() -> BankUiState) {
        _uiState.value = _uiState.value.transform()
    }
}
