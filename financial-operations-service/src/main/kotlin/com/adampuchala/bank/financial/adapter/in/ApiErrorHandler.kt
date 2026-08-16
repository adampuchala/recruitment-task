// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.financial.adapter.`in`

import com.adampuchala.bank.financial.application.AccountNotFoundException
import com.adampuchala.bank.financial.application.IdempotencyConflictException
import com.adampuchala.bank.financial.application.InvalidFinancialRequestException
import com.adampuchala.bank.financial.application.OperationNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException
import java.time.Instant

@RestControllerAdvice
class ApiErrorHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(AccountNotFoundException::class)
    fun accountNotFound(ex: Exception, exchange: ServerWebExchange) = response(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", ex.message!!, exchange)

    @ExceptionHandler(OperationNotFoundException::class)
    fun operationNotFound(ex: Exception, exchange: ServerWebExchange) = response(HttpStatus.NOT_FOUND, "OPERATION_NOT_FOUND", ex.message!!, exchange)

    @ExceptionHandler(IdempotencyConflictException::class)
    fun idempotency(ex: Exception, exchange: ServerWebExchange) = response(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", ex.message!!, exchange)

    @ExceptionHandler(InvalidFinancialRequestException::class)
    fun invalidRequest(ex: Exception, exchange: ServerWebExchange) = response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.message!!, exchange)

    @ExceptionHandler(WebExchangeBindException::class, ServerWebInputException::class)
    fun validation(ex: Exception, exchange: ServerWebExchange): ResponseEntity<ApiError> {
        val missingIdempotencyKey = ex is ServerWebInputException && ex.reason?.contains("Idempotency-Key") == true
        return if (missingIdempotencyKey) {
            response(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required", exchange)
        } else {
            response(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Invalid request", exchange)
        }
    }

    @ExceptionHandler(Exception::class)
    fun unexpected(ex: Exception, exchange: ServerWebExchange): ResponseEntity<ApiError> {
        log.error("Unexpected API error path={} type={}", exchange.request.path.value(), ex.javaClass.simpleName)
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", exchange)
    }

    private fun response(status: HttpStatus, code: String, message: String, exchange: ServerWebExchange) =
        ResponseEntity.status(status).body(ApiError(code, message, Instant.now(), exchange.request.path.value()))
}
