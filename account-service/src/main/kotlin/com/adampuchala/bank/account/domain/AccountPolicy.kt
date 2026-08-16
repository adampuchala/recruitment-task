// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.account.domain

import java.math.BigDecimal

object AccountPolicy {
    fun transitionAllowed(current: AccountStatus, target: AccountStatus, balance: BigDecimal): Boolean {
        if (current == AccountStatus.CLOSED) return false
        if (target == AccountStatus.CLOSED && balance.compareTo(BigDecimal.ZERO) != 0) return false
        return current == target ||
            current == AccountStatus.ACTIVE && target in setOf(AccountStatus.BLOCKED, AccountStatus.CLOSED) ||
            current == AccountStatus.BLOCKED && target in setOf(AccountStatus.ACTIVE, AccountStatus.CLOSED)
    }
}
