// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.financial.domain

import java.math.BigDecimal

object FinancialRules {
    fun withdrawalBalance(current: BigDecimal, amount: BigDecimal): BigDecimal? =
        current.subtract(amount).takeIf { it.signum() >= 0 }

    fun validAmount(amount: BigDecimal): Boolean = amount.signum() > 0 && amount.scale() <= 4
}
