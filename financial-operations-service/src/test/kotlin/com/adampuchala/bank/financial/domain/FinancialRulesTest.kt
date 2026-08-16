// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.financial.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FinancialRulesTest {
    @Test fun `should calculate withdrawal balance`() = assertEquals(BigDecimal("25.00"), FinancialRules.withdrawalBalance(BigDecimal("100.00"), BigDecimal("75.00")))
    @Test fun `should reject withdrawal resulting in negative balance`() = assertNull(FinancialRules.withdrawalBalance(BigDecimal("10.00"), BigDecimal("10.01")))
    @Test fun `should validate positive amount with four decimal places`() {
        assertTrue(FinancialRules.validAmount(BigDecimal("1.0001")))
        assertFalse(FinancialRules.validAmount(BigDecimal("0")))
    }
}
