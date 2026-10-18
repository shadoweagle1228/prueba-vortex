package com.andesretail.stock.web.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import java.time.Instant

@ControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleBadRequest(ex: IllegalArgumentException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail(
            type = "about:blank",
            title = "Bad Request",
            status = HttpStatus.BAD_REQUEST.value(),
            detail = ex.message ?: "Invalid request",
            instance = "",
            timestamp = Instant.now(),
        )
        return ResponseEntity.badRequest().header("Content-Type", "application/problem+json").body(problem)
    }

    @ExceptionHandler(RuntimeException::class)
    fun handleServiceError(ex: RuntimeException): ResponseEntity<ProblemDetail> {
        val problem = ProblemDetail(
            type = "about:blank",
            title = "Service Unavailable",
            status = HttpStatus.SERVICE_UNAVAILABLE.value(),
            detail = ex.message ?: "Service unavailable",
            instance = "",
            timestamp = Instant.now(),
        )
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Content-Type", "application/problem+json").body(problem)
    }

    data class ProblemDetail(
        val type: String,
        val title: String,
        val status: Int,
        val detail: String,
        val instance: String,
        val timestamp: Instant,
    )
}
