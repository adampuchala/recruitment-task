// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.mobile.application

import com.adampuchala.bank.mobile.domain.CreateMobileAccountCommand
import com.adampuchala.bank.mobile.domain.DepositFundsCommand
import com.adampuchala.bank.mobile.domain.DownstreamResult
import com.adampuchala.bank.mobile.domain.MobileAccount
import com.adampuchala.bank.mobile.domain.AccountStatus
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFailsWith

class MobileAccountFacadeTest {
    private val accountService = mock<AccountServicePort>()
    private val financialOperations = mock<FinancialOperationsPort>()
    private val facade = MobileAccountFacade(accountService, financialOperations)

    @Test
    fun `should delegate valid account creation`() = runTest {
        whenever(accountService.create(any(), any())).thenReturn(DownstreamResult.Success(201, account()))

        facade.createAccount(UUID.randomUUID(), CreateMobileAccountCommand("Anna", "Kowalska"))

        verify(accountService).create(any(), any())
    }

    @Test
    fun `should reject invalid deposit before delegation`() = runTest {
        assertFailsWith<InvalidMobileRequestException> {
            facade.deposit(UUID.randomUUID(), UUID.randomUUID(), DepositFundsCommand(BigDecimal.ZERO, null))
        }
    }

    private fun account() = MobileAccount(
        UUID.randomUUID(), "Anna", "Kowalska", BigDecimal.ZERO, AccountStatus.ACTIVE,
        Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"),
    )
}
