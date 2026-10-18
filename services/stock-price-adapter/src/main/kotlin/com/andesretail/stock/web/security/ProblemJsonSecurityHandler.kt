package com.andesretail.stock.web.security

import com.andesretail.stock.web.filter.CorrelationIdFilter
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import java.time.Instant

/**
 * Errores de la cadena de seguridad en el mismo `problem+json` del contrato C-01.
 * Sin esto, el 401 del `AuthenticationEntryPoint` y el 403 del filtro salían sin
 * cuerpo, incumpliendo el esquema `ProblemDetails` (`type`, `instance`,
 * `correlationId`, `timestamp`).
 */
class ProblemJsonSecurityHandler(
    private val objectMapper: ObjectMapper,
) : AuthenticationEntryPoint, AccessDeniedHandler {

    override fun commence(request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) = write(
        request = request,
        response = response,
        status = HttpStatus.UNAUTHORIZED,
        type = "urn:andes:error:unauthorized",
        title = "No autenticado",
        detail = "Se requiere un token de acceso válido para consultar este recurso",
    )

    override fun handle(request: HttpServletRequest, response: HttpServletResponse, accessDeniedException: AccessDeniedException) = write(
        request = request,
        response = response,
        status = HttpStatus.FORBIDDEN,
        type = "urn:andes:error:forbidden",
        title = "Forbidden",
        detail = "No tiene permisos para consultar este recurso",
    )

    private fun write(
        request: HttpServletRequest,
        response: HttpServletResponse,
        status: HttpStatus,
        type: String,
        title: String,
        detail: String,
    ) {
        val correlationId = (request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE) as? String)
            ?: request.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)
            ?: ""
        val body = linkedMapOf<String, Any?>(
            "type" to type,
            "title" to title,
            "status" to status.value(),
            "detail" to detail,
            "instance" to request.requestURI,
            "correlationId" to correlationId,
            "timestamp" to Instant.now().toString(),
        )
        response.status = status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        objectMapper.writeValue(response.writer, body)
    }
}
