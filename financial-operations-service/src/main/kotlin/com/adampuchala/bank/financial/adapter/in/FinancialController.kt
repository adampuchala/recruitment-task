// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.financial.adapter.`in`

import com.adampuchala.bank.financial.application.CommandResult
import com.adampuchala.bank.financial.application.FinancialApplicationService
import com.adampuchala.bank.financial.application.OperationPage
import com.adampuchala.bank.financial.application.OperationResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class MoneyRequest(
    @field:DecimalMin(value = "0.0001") @field:Digits(integer = 15, fraction = 4) val amount: BigDecimal,
    @field:Size(max = 500) val description: String? = null,
)

data class TransferRequest(
    val fromAccountId: UUID,
    val toAccountId: UUID,
    @field:DecimalMin(value = "0.0001") @field:Digits(integer = 15, fraction = 4) val amount: BigDecimal,
    @field:Size(max = 500) val description: String? = null,
)

data class ApiError(val code: String, val message: String, val timestamp: Instant, val path: String)

@RestController
@RequestMapping("/api/v1")
class FinancialController(private val service: FinancialApplicationService) {
    @PostMapping("/accounts/{accountId}/deposits")
    suspend fun deposit(@PathVariable accountId: UUID, @RequestHeader("Idempotency-Key") key: UUID, @Valid @RequestBody request: MoneyRequest): ResponseEntity<*> =
        toResponse(service.deposit(key, accountId, request.amount, request.description), "/api/v1/accounts/$accountId/deposits")

    @PostMapping("/accounts/{accountId}/withdrawals")
    suspend fun withdrawal(@PathVariable accountId: UUID, @RequestHeader("Idempotency-Key") key: UUID, @Valid @RequestBody request: MoneyRequest): ResponseEntity<*> =
        toResponse(service.withdrawal(key, accountId, request.amount, request.description), "/api/v1/accounts/$accountId/withdrawals")

    @PostMapping("/transfers")
    suspend fun transfer(@RequestHeader("Idempotency-Key") key: UUID, @Valid @RequestBody request: TransferRequest): ResponseEntity<*> =
        toResponse(service.transfer(key, request.fromAccountId, request.toAccountId, request.amount, request.description), "/api/v1/transfers")

    @GetMapping("/accounts/{accountId}/operations")
    suspend fun history(@PathVariable accountId: UUID, @RequestParam(defaultValue = "0") page: Int, @RequestParam(defaultValue = "20") size: Int): OperationPage =
        service.history(accountId, page, size)

    @GetMapping("/operations/{operationId}")
    suspend fun get(@PathVariable operationId: UUID): OperationResponse = service.get(operationId)

    private fun toResponse(result: CommandResult, path: String): ResponseEntity<*> = when (result) {
        is CommandResult.Accepted -> ResponseEntity.status(if (result.replay) HttpStatus.OK else HttpStatus.CREATED).body(result.response)
        is CommandResult.Rejected -> ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError(result.error.code, result.error.message, Instant.now(), path))
    }
}
