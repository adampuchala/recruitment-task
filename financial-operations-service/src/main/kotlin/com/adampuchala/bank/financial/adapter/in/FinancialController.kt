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
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
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
    @Operation(summary = "Deposit funds")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Created", content = [Content(schema = Schema(implementation = OperationResponse::class))]),
        ApiResponse(responseCode = "200", description = "Idempotent replay", content = [Content(schema = Schema(implementation = OperationResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "404", description = "Account not found", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "409", description = "Business conflict", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "500", description = "Unexpected error", content = [Content(schema = Schema(implementation = ApiError::class))]),
    )
    suspend fun deposit(@PathVariable accountId: UUID, @RequestHeader("Idempotency-Key") key: UUID, @Valid @RequestBody request: MoneyRequest): ResponseEntity<*> =
        toResponse(service.deposit(key, accountId, request.amount, request.description), "/api/v1/accounts/$accountId/deposits")

    @PostMapping("/accounts/{accountId}/withdrawals")
    @Operation(summary = "Withdraw funds")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Created", content = [Content(schema = Schema(implementation = OperationResponse::class))]),
        ApiResponse(responseCode = "200", description = "Idempotent replay", content = [Content(schema = Schema(implementation = OperationResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "404", description = "Account not found", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "409", description = "Business conflict", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "500", description = "Unexpected error", content = [Content(schema = Schema(implementation = ApiError::class))]),
    )
    suspend fun withdrawal(@PathVariable accountId: UUID, @RequestHeader("Idempotency-Key") key: UUID, @Valid @RequestBody request: MoneyRequest): ResponseEntity<*> =
        toResponse(service.withdrawal(key, accountId, request.amount, request.description), "/api/v1/accounts/$accountId/withdrawals")

    @PostMapping("/transfers")
    @Operation(summary = "Transfer funds")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Created", content = [Content(schema = Schema(implementation = OperationResponse::class))]),
        ApiResponse(responseCode = "200", description = "Idempotent replay", content = [Content(schema = Schema(implementation = OperationResponse::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "404", description = "Account not found", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "409", description = "Business conflict", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "500", description = "Unexpected error", content = [Content(schema = Schema(implementation = ApiError::class))]),
    )
    suspend fun transfer(@RequestHeader("Idempotency-Key") key: UUID, @Valid @RequestBody request: TransferRequest): ResponseEntity<*> =
        toResponse(service.transfer(key, request.fromAccountId, request.toAccountId, request.amount, request.description), "/api/v1/transfers")

    @GetMapping("/accounts/{accountId}/operations")
    @Operation(summary = "Retrieve account operation history")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Operation page", content = [Content(schema = Schema(implementation = OperationPage::class))]),
        ApiResponse(responseCode = "400", description = "Invalid pagination", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "500", description = "Unexpected error", content = [Content(schema = Schema(implementation = ApiError::class))]),
    )
    suspend fun history(
        @PathVariable accountId: UUID,
        @Parameter(schema = Schema(minimum = "0", defaultValue = "0")) @RequestParam(defaultValue = "0") page: Int,
        @Parameter(schema = Schema(minimum = "1", maximum = "100", defaultValue = "20")) @RequestParam(defaultValue = "20") size: Int,
    ): OperationPage =
        service.history(accountId, page, size)

    @GetMapping("/operations/{operationId}")
    @Operation(summary = "Retrieve operation by ID")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Operation", content = [Content(schema = Schema(implementation = OperationResponse::class))]),
        ApiResponse(responseCode = "404", description = "Operation not found", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "500", description = "Unexpected error", content = [Content(schema = Schema(implementation = ApiError::class))]),
    )
    suspend fun get(@PathVariable operationId: UUID): OperationResponse = service.get(operationId)

    private fun toResponse(result: CommandResult, path: String): ResponseEntity<*> = when (result) {
        is CommandResult.Accepted -> ResponseEntity.status(if (result.replay) HttpStatus.OK else HttpStatus.CREATED).body(result.response)
        is CommandResult.Rejected -> ResponseEntity.status(HttpStatus.CONFLICT).body(ApiError(result.error.code, result.error.message, result.error.timestamp, path))
    }
}
