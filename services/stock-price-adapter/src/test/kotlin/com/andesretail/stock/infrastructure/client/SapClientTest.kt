package com.andesretail.stock.infrastructure.client

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.util.concurrent.TimeUnit

class SapClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: SapClient

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = SapClient(WebClient.builder(), server.url("/").toString(), 3000L)
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `deserializa el payload de SAP y arma la URL esperada`() {
        val body = listOf(
            "\"Material\":\"SKU-1\"",
            "\"Plant\":\"W01\"",
            "\"UnrestrictedStock\":100",
            "\"ReservedStock\":10",
            "\"LastChanged\":\"2026-10-18T00:00:00Z\"",
        ).joinToString(",", "{", "}")
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )

        val response = client.fetchStock("SKU-1", "W01")

        assertThat(response.material).isEqualTo("SKU-1")
        assertThat(response.plant).isEqualTo("W01")
        assertThat(response.unrestrictedStock).isEqualTo(100)
        assertThat(response.reservedStock).isEqualTo(10)

        val recorded = server.takeRequest()
        assertThat(recorded.path).contains("/sap/opu/odata/sap/API_STOCK/material/SKU-1")
        assertThat(recorded.path).contains("plant=W01")
    }

    @Test
    fun `propaga el token del portador cuando se entrega`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"Material":"SKU-1","Plant":"W01"}"""),
        )

        client.fetchStock("SKU-1", "W01", "token-123")

        val recorded = server.takeRequest()
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer token-123")
    }

    @Test
    fun `traduce un error HTTP a SapClientException`() {
        server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))

        assertThatThrownBy { client.fetchStock("SKU-1", "W01") }
            .isInstanceOf(SapClientException::class.java)
            .hasMessageContaining("SAP respondió 500")
    }

    @Test
    fun `traduce una caida de red a SapClientException`() {
        server.shutdown()

        assertThatThrownBy { client.fetchStock("SKU-1", "W01") }
            .isInstanceOf(SapClientException::class.java)
            .hasMessageContaining("Llamada a SAP fallida")
    }

    /**
     * Presupuesto de latencia de NFR-01/BR-STK-02: con el tiempo por defecto
     * (250 ms) una respuesta que tarda un segundo debe cortarse y traducirse a
     * `SapClientException`, no esperar al ERP.
     */
    @Test
    fun `corta la llamada cuando SAP excede el presupuesto de tiempo`() {
        client = SapClient(WebClient.builder(), server.url("/").toString())
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""{"Material":"SKU-1","Plant":"W01"}""")
                .setBodyDelay(1, TimeUnit.SECONDS),
        )

        val inicio = System.nanoTime()
        assertThatThrownBy { client.fetchStock("SKU-1", "W01") }
            .isInstanceOf(SapClientException::class.java)
        val transcurrido = Duration.ofNanos(System.nanoTime() - inicio)

        assertThat(transcurrido).isLessThan(Duration.ofMillis(1000))
    }
}
