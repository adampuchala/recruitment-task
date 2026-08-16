// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.mobile.adapter.`in`

import com.adampuchala.bank.mobile.application.MobileAccountFacade
import com.adampuchala.bank.mobile.domain.AccountStatus
import com.adampuchala.bank.mobile.domain.CreateMobileAccountCommand
import com.adampuchala.bank.mobile.domain.DepositFundsCommand
import com.adampuchala.bank.mobile.domain.DownstreamResult
import com.adampuchala.bank.mobile.domain.MobileAccount
import com.adampuchala.bank.mobile.domain.MobileDeposit
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CreateAccountRequest(@field:NotBlank @field:Size(max = 100) val firstName: String, @field:NotBlank @field:Size(max = 100) val lastName: String)
data class DepositRequest(@field:DecimalMin("0.0001") @field:Digits(integer = 15, fraction = 4) val amount: BigDecimal, @field:Size(max = 500) val description: String? = null)
data class AccountResponse(val accountId: UUID, val firstName: String, val lastName: String, val balance: BigDecimal, val status: AccountStatus, val createdAt: Instant, val updatedAt: Instant)
data class DepositResponse(val operationId: UUID, val type: String, val status: String, val fromAccountId: UUID?, val toAccountId: UUID, val amount: BigDecimal, val balanceAfter: BigDecimal, val description: String?, val createdAt: Instant)
data class ApiError(val code: String, val message: String, val timestamp: Instant, val path: String)

@RestController
@RequestMapping("/api/v1/mobile/accounts")
class MobileController(private val facade: MobileAccountFacade) {
    @PostMapping
    @Operation(summary = "Create an account")
    @ApiResponses(
        ApiResponse(responseCode = "201", content = [Content(schema = Schema(implementation = AccountResponse::class))]),
        ApiResponse(responseCode = "200", description = "Idempotent replay", content = [Content(schema = Schema(implementation = AccountResponse::class))]),
        ApiResponse(responseCode = "400", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "409", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "503", content = [Content(schema = Schema(implementation = ApiError::class))]),
    )
    suspend fun create(@RequestHeader("Idempotency-Key") key: UUID, @Valid @RequestBody request: CreateAccountRequest): ResponseEntity<Any> =
        response(facade.createAccount(key, CreateMobileAccountCommand(request.firstName, request.lastName)), "/api/v1/mobile/accounts") { it.toResponse() }

    @GetMapping("/{accountId}")
    @Operation(summary = "Retrieve account details")
    @ApiResponses(
        ApiResponse(responseCode = "200", content = [Content(schema = Schema(implementation = AccountResponse::class))]),
        ApiResponse(responseCode = "404", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "503", content = [Content(schema = Schema(implementation = ApiError::class))]),
    )
    suspend fun get(@PathVariable accountId: UUID): ResponseEntity<Any> =
        response(facade.getAccount(accountId), "/api/v1/mobile/accounts/$accountId") { it.toResponse() }

    @PostMapping("/{accountId}/deposits")
    @Operation(summary = "Deposit funds")
    @ApiResponses(
        ApiResponse(responseCode = "201", content = [Content(schema = Schema(implementation = DepositResponse::class))]),
        ApiResponse(responseCode = "200", description = "Idempotent replay", content = [Content(schema = Schema(implementation = DepositResponse::class))]),
        ApiResponse(responseCode = "400", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "404", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "409", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "503", content = [Content(schema = Schema(implementation = ApiError::class))]),
    )
    suspend fun deposit(@PathVariable accountId: UUID, @RequestHeader("Idempotency-Key") key: UUID, @Valid @RequestBody request: DepositRequest): ResponseEntity<Any> =
        response(facade.deposit(key, accountId, DepositFundsCommand(request.amount, request.description)), "/api/v1/mobile/accounts/$accountId/deposits") { it.toResponse() }

    private fun <T : Any, R : Any> response(result: DownstreamResult<T>, path: String, mapBody: (T) -> R): ResponseEntity<Any> = when (result) {
        is DownstreamResult.Success -> ResponseEntity.status(result.statusCode).body(mapBody(result.body))
        is DownstreamResult.Failure -> ResponseEntity.status(result.statusCode).body(ApiError(result.error.code, result.error.message, Instant.now(), path))
    }

    private fun MobileAccount.toResponse() = AccountResponse(accountId, firstName, lastName, balance, status, createdAt, updatedAt)
    private fun MobileDeposit.toResponse() = DepositResponse(operationId, type, status, fromAccountId, toAccountId, amount, balanceAfter, description, createdAt)
}
