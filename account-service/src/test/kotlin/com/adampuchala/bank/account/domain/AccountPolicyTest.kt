// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.account.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountPolicyTest {
    @Test fun `should allow blocking active account`() = assertTrue(AccountPolicy.transitionAllowed(AccountStatus.ACTIVE, AccountStatus.BLOCKED, BigDecimal.ZERO))
    @Test fun `should reject closing account with balance`() = assertFalse(AccountPolicy.transitionAllowed(AccountStatus.ACTIVE, AccountStatus.CLOSED, BigDecimal.ONE))
    @Test fun `should reject transition from closed account`() = assertFalse(AccountPolicy.transitionAllowed(AccountStatus.CLOSED, AccountStatus.ACTIVE, BigDecimal.ZERO))
}
