package com.andesretail.stock.web.exception

import com.andesretail.stock.domain.exception.ErpStockUnavailableException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.access.AccessDeniedException

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()
    private val request = MockHttpServletRequest().apply {
        requestURI = "/v1/inventory/stock/ABC-123"
        addHeader("X-Correlation-ID", "corr-1")
    }

    @Test
    fun `traduce IllegalArgumentException a 400`() {
        val problem = handler.handleBadRequest(IllegalArgumentException("sku invalido"), request)

        assertThat(problem.status).isEqualTo(400)
        assertThat(problem.title).isEqualTo("Solicitud inválida")
        assertThat(problem.detail).isEqualTo("sku invalido")
        assertThat(problem.type.toString()).isEqualTo("urn:andes:error:bad-request")
        assertThat(problem.instance.toString()).isEqualTo("/v1/inventory/stock/ABC-123")
        assertThat(problem.properties?.get("correlationId")).isEqualTo("corr-1")
    }

    @Test
    fun `traduce AccessDeniedException a 403 y no a 500`() {
        val problem = handler.handleAccessDenied(AccessDeniedException("denegado"), request)

        assertThat(problem.status).isEqualTo(403)
        assertThat(problem.title).isEqualTo("Forbidden")
        assertThat(problem.detail).doesNotContain("denegado")
    }

    @Test
    fun `traduce indisponibilidad del ERP a 503 con Retry-After y sin filtrar detalle`() {
        val response = handler.handleUpstreamUnavailable(
            ErpStockUnavailableException("SAP caido en 10.0.0.5"),
            request,
        )

        assertThat(response.statusCode.value()).isEqualTo(503)
        assertThat(response.headers.getFirst("Retry-After")).isEqualTo("5")
        assertThat(response.body!!.detail).doesNotContain("10.0.0.5")
        assertThat(response.body!!.type.toString()).isEqualTo("urn:andes:error:stock-unavailable")
    }

    @Test
    fun `traduce un error inesperado a 500`() {
        val problem = handler.handleUnexpected(RuntimeException("boom"), request)

        assertThat(problem.status).isEqualTo(500)
        assertThat(problem.detail).doesNotContain("boom")
    }
}
