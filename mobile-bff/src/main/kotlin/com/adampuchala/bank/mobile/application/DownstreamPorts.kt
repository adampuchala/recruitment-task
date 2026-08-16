// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.mobile.application

import com.adampuchala.bank.mobile.domain.CreateMobileAccountCommand
import com.adampuchala.bank.mobile.domain.DepositFundsCommand
import com.adampuchala.bank.mobile.domain.DownstreamResult
import com.adampuchala.bank.mobile.domain.MobileAccount
import com.adampuchala.bank.mobile.domain.MobileDeposit
import java.util.UUID

interface AccountServicePort {
    suspend fun create(idempotencyKey: UUID, command: CreateMobileAccountCommand): DownstreamResult<MobileAccount>
    suspend fun get(accountId: UUID): DownstreamResult<MobileAccount>
}

interface FinancialOperationsPort {
    suspend fun deposit(idempotencyKey: UUID, accountId: UUID, command: DepositFundsCommand): DownstreamResult<MobileDeposit>
}
