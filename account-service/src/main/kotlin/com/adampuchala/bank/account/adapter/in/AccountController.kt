// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.account.adapter.`in`

import com.adampuchala.bank.account.application.AccountApplicationService
import com.adampuchala.bank.account.application.AccountView
import com.adampuchala.bank.account.domain.AccountStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

data class CreateAccountRequest(
    @field:NotBlank @field:Size(max = 100) val firstName: String,
    @field:NotBlank @field:Size(max = 100) val lastName: String,
)

data class ChangeStatusRequest(val status: AccountStatus)

@RestController
@RequestMapping("/api/v1/accounts")
class AccountController(private val service: AccountApplicationService) {
    @PostMapping
    @Operation(summary = "Create an account idempotently")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Created", content = [Content(schema = Schema(implementation = AccountView::class))]),
        ApiResponse(responseCode = "200", description = "Idempotent replay", content = [Content(schema = Schema(implementation = AccountView::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "409", description = "Idempotency conflict", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "500", description = "Unexpected error", content = [Content(schema = Schema(implementation = ApiError::class))]),
    )
    suspend fun create(
        @RequestHeader("Idempotency-Key") idempotencyKey: UUID,
        @Valid @RequestBody request: CreateAccountRequest,
    ): ResponseEntity<AccountView> {
        val result = service.create(idempotencyKey, request.firstName, request.lastName)
        return ResponseEntity.status(if (result.replay) HttpStatus.OK else HttpStatus.CREATED).body(result.account)
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get account details")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Account", content = [Content(schema = Schema(implementation = AccountView::class))]),
        ApiResponse(responseCode = "404", description = "Account not found", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "500", description = "Unexpected error", content = [Content(schema = Schema(implementation = ApiError::class))]),
    )
    suspend fun get(@PathVariable accountId: UUID): AccountView = service.get(accountId)

    @PutMapping("/{accountId}/status")
    @Operation(summary = "Change account status")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Updated account", content = [Content(schema = Schema(implementation = AccountView::class))]),
        ApiResponse(responseCode = "400", description = "Invalid request", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "404", description = "Account not found", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "409", description = "Invalid status transition", content = [Content(schema = Schema(implementation = ApiError::class))]),
        ApiResponse(responseCode = "500", description = "Unexpected error", content = [Content(schema = Schema(implementation = ApiError::class))]),
    )
    suspend fun changeStatus(
        @PathVariable accountId: UUID,
        @Valid @RequestBody request: ChangeStatusRequest,
    ): AccountView = service.changeStatus(accountId, request.status)
}
