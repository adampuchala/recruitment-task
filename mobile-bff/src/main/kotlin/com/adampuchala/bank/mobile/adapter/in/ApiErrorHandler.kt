// Copyright (c) Adam Puchała Software Engineering. For recruitment purposes only.
package com.adampuchala.bank.mobile.adapter.`in`

import com.adampuchala.bank.mobile.application.InvalidMobileRequestException
import com.adampuchala.bank.mobile.domain.DownstreamUnavailableException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.ServerWebInputException
import java.time.Instant

@RestControllerAdvice
class ApiErrorHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(InvalidMobileRequestException::class, WebExchangeBindException::class, ServerWebInputException::class)
    fun validation(exception: Exception, exchange: ServerWebExchange): ResponseEntity<ApiError> =
        error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", exception.message ?: "Invalid request", exchange)

    @ExceptionHandler(DownstreamUnavailableException::class)
    fun unavailable(exception: DownstreamUnavailableException, exchange: ServerWebExchange): ResponseEntity<ApiError> {
        log.warn("Downstream unavailable path={} service={}", exchange.request.path.value(), exception.message)
        return error(HttpStatus.SERVICE_UNAVAILABLE, "DOWNSTREAM_UNAVAILABLE", "A required backend service is unavailable", exchange)
    }

    @ExceptionHandler(Exception::class)
    fun unexpected(exception: Exception, exchange: ServerWebExchange): ResponseEntity<ApiError> {
        log.error("Unexpected BFF error path={} type={}", exchange.request.path.value(), exception.javaClass.simpleName)
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", exchange)
    }

    private fun error(status: HttpStatus, code: String, message: String, exchange: ServerWebExchange) =
        ResponseEntity.status(status).body(ApiError(code, message, Instant.now(), exchange.request.path.value()))
}
