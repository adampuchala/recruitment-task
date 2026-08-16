// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.account.adapter.`in`

import com.adampuchala.bank.account.application.AccountApplicationService
import com.adampuchala.bank.account.application.AccountView
import com.adampuchala.bank.account.domain.AccountStatus
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
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
import reactor.core.publisher.Mono
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
    fun create(
        @RequestHeader("Idempotency-Key") idempotencyKey: UUID,
        @Valid @RequestBody request: CreateAccountRequest,
    ): Mono<ResponseEntity<AccountView>> = service.create(idempotencyKey, request.firstName, request.lastName)
        .map { ResponseEntity.status(if (it.replay) HttpStatus.OK else HttpStatus.CREATED).body(it.account) }

    @GetMapping("/{accountId}")
    fun get(@PathVariable accountId: UUID): Mono<AccountView> = service.get(accountId)

    @PutMapping("/{accountId}/status")
    fun changeStatus(
        @PathVariable accountId: UUID,
        @Valid @RequestBody request: ChangeStatusRequest,
    ): Mono<AccountView> = service.changeStatus(accountId, request.status)
}
