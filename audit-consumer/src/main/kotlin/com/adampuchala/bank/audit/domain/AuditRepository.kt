// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.audit.domain

import com.adampuchala.bank.contracts.FinancialOperationCompleted

interface AuditRepository {
    suspend fun insertIfAbsent(event: FinancialOperationCompleted): Boolean
}
