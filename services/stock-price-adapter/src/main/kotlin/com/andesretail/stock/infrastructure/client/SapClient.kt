package com.andesretail.stock.infrastructure.client

import io.netty.channel.ChannelOption
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.netty.http.client.HttpClient
import reactor.netty.resources.ConnectionProvider
import java.time.Duration

/**
 * Cliente HTTP del sistema legacy SAP (API_STOCK / OData).
 *
 * Aísla el transporte: expone una operación tipada y traduce cualquier fallo de
 * red o de protocolo a [SapClientException] para que la capa de resiliencia
 * (CircuitBreaker / Bulkhead) pueda contabilizarlo y abrir el circuito.
 *
 * El presupuesto de tiempo y el pool son los del diseño de rendimiento: conexión
 * 100 ms, lectura 250 ms estricta y un máximo de 20 conexiones con espera máxima
 * de 100 ms antes de rechazar (NFR-12 / BR-STK-05), de modo que un miss de caché
 * nunca retiene la petición más allá del p95 objetivo.
 */
@Component
class SapClient(
    webClientBuilder: WebClient.Builder,
    @Value("\${sap.base-url:http://localhost:9000}") baseUrl: String,
    @Value("\${sap.timeout-millis:250}") private val timeoutMillis: Long = 250,
    @Value("\${sap.connect-timeout-millis:100}") connectTimeoutMillis: Int = 100,
    @Value("\${sap.max-connections:20}") maxConnections: Int = 20,
) {
    private val webClient: WebClient = webClientBuilder
        .clientConnector(ReactorClientHttpConnector(createHttpClient(connectTimeoutMillis, timeoutMillis, maxConnections)))
        .baseUrl(baseUrl)
        .build()

    fun fetchStock(sku: String, warehouseId: String, bearerToken: String? = null): SapStockResponse {
        val timeout = Duration.ofMillis(timeoutMillis)
        return try {
            webClient.get()
                .uri { builder ->
                    builder.path("/sap/opu/odata/sap/API_STOCK/material/{sku}")
                        .queryParam("plant", warehouseId)
                        .build(sku)
                }
                .headers { headers ->
                    if (!bearerToken.isNullOrBlank()) {
                        headers.set(HttpHeaders.AUTHORIZATION, "Bearer $bearerToken")
                    }
                }
                .retrieve()
                .bodyToMono(SapStockResponse::class.java)
                .block(timeout)
                ?: throw SapClientException("SAP devolvió un cuerpo vacío para sku=$sku plant=$warehouseId")
        } catch (ex: WebClientResponseException) {
            throw SapClientException("SAP respondió ${ex.statusCode.value()} para sku=$sku: ${ex.responseBodyAsString}", ex)
        } catch (ex: SapClientException) {
            throw ex
        } catch (ex: Exception) {
            throw SapClientException("Llamada a SAP fallida para sku=$sku: ${ex.message}", ex)
        }
    }

    private fun createHttpClient(connectTimeoutMillis: Int, readTimeoutMillis: Long, maxConnections: Int): HttpClient {
        val provider = ConnectionProvider.builder("sap-pool")
            .maxConnections(maxConnections)
            .pendingAcquireTimeout(Duration.ofMillis(READ_TIMEOUT_PENDING_MILLIS))
            .build()
        return HttpClient.create(provider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMillis)
            .responseTimeout(Duration.ofMillis(readTimeoutMillis))
    }

    companion object {
        /** Espera máxima por una conexión libre del pool antes de rechazar hacia el fallback (BR-STK-05). */
        private const val READ_TIMEOUT_PENDING_MILLIS = 100L
    }
}

class SapClientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
