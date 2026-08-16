// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.account.adapter.`in`

import com.adampuchala.bank.account.application.AccountNotFoundException
import com.adampuchala.bank.account.application.IdempotencyConflictException
import com.adampuchala.bank.account.application.InvalidStatusTransitionException
import com.adampuchala.bank.account.application.NonZeroBalanceException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException
import java.time.Instant

data class ApiError(val code: String, val message: String, val timestamp: Instant, val path: String)

@RestControllerAdvice
class ApiErrorHandler {
    @ExceptionHandler(AccountNotFoundException::class)
    fun notFound(ex: AccountNotFoundException, exchange: ServerWebExchange) = response(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", ex.message!!, exchange)

    @ExceptionHandler(IdempotencyConflictException::class)
    fun idempotency(ex: IdempotencyConflictException, exchange: ServerWebExchange) = response(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", ex.message!!, exchange)

    @ExceptionHandler(InvalidStatusTransitionException::class, NonZeroBalanceException::class)
    fun conflict(ex: RuntimeException, exchange: ServerWebExchange) = response(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION", ex.message!!, exchange)

    @ExceptionHandler(WebExchangeBindException::class, ServerWebInputException::class)
    fun validation(ex: Exception, exchange: ServerWebExchange): ResponseEntity<ApiError> {
        val missingIdempotencyKey = ex is ServerWebInputException && ex.reason?.contains("Idempotency-Key") == true
        return if (missingIdempotencyKey) {
            response(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required", exchange)
        } else {
            response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request", exchange)
        }
    }

    private fun response(status: HttpStatus, code: String, message: String, exchange: ServerWebExchange) =
        ResponseEntity.status(status).body(ApiError(code, message, Instant.now(), exchange.request.path.value()))
}
