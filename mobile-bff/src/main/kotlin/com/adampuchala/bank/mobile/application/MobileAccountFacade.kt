// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.mobile.application

import com.adampuchala.bank.mobile.domain.CreateMobileAccountCommand
import com.adampuchala.bank.mobile.domain.DepositFundsCommand
import com.adampuchala.bank.mobile.domain.DownstreamResult
import com.adampuchala.bank.mobile.domain.MobileAccount
import com.adampuchala.bank.mobile.domain.MobileDeposit
import org.springframework.stereotype.Service
import java.util.UUID

class InvalidMobileRequestException(message: String) : RuntimeException(message)

@Service
class MobileAccountFacade(
    private val accountService: AccountServicePort,
    private val financialOperations: FinancialOperationsPort,
) {
    suspend fun createAccount(idempotencyKey: UUID, command: CreateMobileAccountCommand): DownstreamResult<MobileAccount> {
        if (command.firstName.isBlank() || command.lastName.isBlank()) throw InvalidMobileRequestException("Account holder names must not be blank")
        return accountService.create(idempotencyKey, command)
    }

    suspend fun getAccount(accountId: UUID): DownstreamResult<MobileAccount> = accountService.get(accountId)

    suspend fun deposit(idempotencyKey: UUID, accountId: UUID, command: DepositFundsCommand): DownstreamResult<MobileDeposit> {
        if (command.amount.signum() <= 0 || command.amount.scale() > 4) throw InvalidMobileRequestException("Amount must be positive with at most four fractional digits")
        if (command.description != null && command.description.length > 500) throw InvalidMobileRequestException("Description must not exceed 500 characters")
        return financialOperations.deposit(idempotencyKey, accountId, command)
    }
}
