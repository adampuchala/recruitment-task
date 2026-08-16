// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.audit.domain

import com.adampuchala.bank.contracts.FinancialOperationCompleted
import reactor.core.publisher.Mono

interface AuditRepository {
    fun insertIfAbsent(event: FinancialOperationCompleted): Mono<Boolean>
}
