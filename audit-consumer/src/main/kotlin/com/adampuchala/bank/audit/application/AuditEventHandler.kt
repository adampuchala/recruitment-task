// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.audit.application

import com.adampuchala.bank.audit.domain.AuditRepository
import com.adampuchala.bank.contracts.FinancialOperationCompleted
import org.springframework.stereotype.Service
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait

interface AuditEventHandler {
    suspend fun handle(event: FinancialOperationCompleted): Boolean
}

@Service
class TransactionalAuditEventHandler(
    private val repository: AuditRepository,
    private val transactionalOperator: TransactionalOperator,
) : AuditEventHandler {
    override suspend fun handle(event: FinancialOperationCompleted): Boolean = transactionalOperator.executeAndAwait {
        repository.insertIfAbsent(event)
    }
}
