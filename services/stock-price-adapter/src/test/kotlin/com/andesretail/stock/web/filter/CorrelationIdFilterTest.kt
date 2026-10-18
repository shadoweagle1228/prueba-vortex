package com.andesretail.stock.web.filter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class CorrelationIdFilterTest {

    private val filter = CorrelationIdFilter()

    @Test
    fun `propaga el correlation id entrante`() {
        val request = MockHttpServletRequest().apply { addHeader("X-Correlation-ID", "corr-123") }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertThat(response.getHeader("X-Correlation-ID")).isEqualTo("corr-123")
    }

    @Test
    fun `publica el correlation id como atributo de la peticion`() {
        val request = MockHttpServletRequest().apply { addHeader("X-Correlation-ID", "corr-abc") }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        // Los manejadores de error leen este atributo para el campo correlationId del problem+json.
        assertThat(request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE)).isEqualTo("corr-abc")
    }

    @Test
    fun `genera un correlation id cuando no viene`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertThat(response.getHeader("X-Correlation-ID")).isNotBlank()
        assertThat(request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE))
            .isEqualTo(response.getHeader("X-Correlation-ID"))
    }

    @Test
    fun `rechaza un correlation id desmesurado y genera uno nuevo`() {
        val request = MockHttpServletRequest().apply { addHeader("X-Correlation-ID", "x".repeat(200)) }
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertThat(response.getHeader("X-Correlation-ID")).hasSizeLessThan(200)
    }

    @Test
    fun `limpia el MDC al terminar`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()

        filter.doFilter(request, response, MockFilterChain())

        assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE)).isNull()
    }
}
