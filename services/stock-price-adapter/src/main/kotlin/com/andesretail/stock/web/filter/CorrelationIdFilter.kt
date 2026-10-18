package com.andesretail.stock.web.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Propaga el identificador de correlación exigido por NFR-08 y por el contrato C-01.
 *
 * Resuelve el id una sola vez por petición y lo publica en tres sitios
 * consistentes: el MDC de los logs estructurados, la cabecera `X-Correlation-ID`
 * de la respuesta y un atributo de la petición que consumen los manejadores de
 * error. Así el `correlationId` del `problem+json` nunca queda vacío aunque el
 * cliente no envíe la cabecera.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val correlationId = resolveCorrelationId(request)
        request.setAttribute(CORRELATION_ID_ATTRIBUTE, correlationId)
        MDC.put(CORRELATION_ID_ATTRIBUTE, correlationId)
        response.setHeader(CORRELATION_ID_HEADER, correlationId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(CORRELATION_ID_ATTRIBUTE)
        }
    }

    private fun resolveCorrelationId(request: HttpServletRequest): String {
        val incoming = request.getHeader(CORRELATION_ID_HEADER)?.trim()
        return if (!incoming.isNullOrEmpty() && incoming.length <= MAX_CORRELATION_ID_LENGTH) {
            incoming
        } else {
            UUID.randomUUID().toString()
        }
    }

    companion object {
        const val CORRELATION_ID_HEADER = "X-Correlation-ID"

        /** Clave compartida entre el MDC, el atributo de petición y los manejadores de error. */
        const val CORRELATION_ID_ATTRIBUTE = "correlationId"

        private const val MAX_CORRELATION_ID_LENGTH = 128
    }
}
