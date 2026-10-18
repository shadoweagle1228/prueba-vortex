package com.andesretail.stock.web.exception

import com.andesretail.stock.domain.exception.ErpStockUnavailableException
import com.andesretail.stock.web.filter.CorrelationIdFilter
import io.github.resilience4j.bulkhead.BulkheadFullException
import io.github.resilience4j.circuitbreaker.CallNotPermittedException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI
import java.time.Instant

/**
 * Manejo centralizado de errores con el formato RFC 7807 (`application/problem+json`).
 *
 * Cada familia de fallo se traduce a un problema estable que cumple el esquema
 * `ProblemDetails` del contrato C-01: `type` (URN del error), `title`, `status`,
 * `detail`, `instance`, `correlationId` y `timestamp`. El 503 de indisponibilidad
 * añade `Retry-After`, tal como exige BR-STK-04.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(
        IllegalArgumentException::class,
        MethodArgumentNotValidException::class,
        MissingServletRequestParameterException::class,
    )
    fun handleBadRequest(ex: Exception, request: HttpServletRequest): ProblemDetail = problem(
        status = HttpStatus.BAD_REQUEST,
        type = "urn:andes:error:bad-request",
        title = "Solicitud inválida",
        detail = ex.message ?: "Solicitud inválida",
        request = request,
    )

    /**
     * `@PreAuthorize` lanza `AuthorizationDeniedException` (subclase de
     * `AccessDeniedException`) dentro de `DispatcherServlet`. Sin este handler
     * la familia `Exception` la devolvería como 500 y el consumidor no podría
     * distinguir un 403 de un fallo real del servidor.
     */
    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException, request: HttpServletRequest): ProblemDetail {
        log.debug("Acceso denegado: {}", ex.message)
        return problem(
            status = HttpStatus.FORBIDDEN,
            type = "urn:andes:error:forbidden",
            title = "Forbidden",
            detail = "No tiene permisos para consultar este recurso",
            request = request,
        )
    }

    @ExceptionHandler(ErpStockUnavailableException::class, CallNotPermittedException::class, BulkheadFullException::class)
    fun handleUpstreamUnavailable(ex: Exception, request: HttpServletRequest): ResponseEntity<ProblemDetail> {
        // No se filtra el detalle interno del ERP al consumidor.
        log.warn("ERP no disponible: {}", ex.message)
        val body = problem(
            status = HttpStatus.SERVICE_UNAVAILABLE,
            type = "urn:andes:error:stock-unavailable",
            title = "Stock no disponible",
            detail = "El sistema de inventario no está disponible en este momento",
            request = request,
        )
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS.toString())
            .body(body)
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception, request: HttpServletRequest): ProblemDetail {
        log.error("Error no controlado", ex)
        return problem(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            type = "urn:andes:error:internal",
            title = "Error interno",
            detail = "Se produjo un error inesperado al procesar la solicitud",
            request = request,
        )
    }

    private fun problem(status: HttpStatus, type: String, title: String, detail: String, request: HttpServletRequest): ProblemDetail {
        val problem = ProblemDetail.forStatusAndDetail(status, detail)
        problem.type = URI.create(type)
        problem.title = title
        problem.instance = URI.create(request.requestURI)
        problem.setProperty("correlationId", correlationId(request))
        problem.setProperty("timestamp", Instant.now())
        return problem
    }

    /** Prefiere el id que generó [CorrelationIdFilter]; cae al de la cabecera si el filtro no corrió. */
    private fun correlationId(request: HttpServletRequest): String =
        (request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE) as? String)
            ?: request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)
            ?: ""

    companion object {
        /** Segundos que el consumidor debe esperar antes de reintentar (contrato C-01, `ServiceUnavailable`). */
        const val RETRY_AFTER_SECONDS = 5
    }
}
