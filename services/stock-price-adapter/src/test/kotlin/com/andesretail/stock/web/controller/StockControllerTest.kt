package com.andesretail.stock.web.controller

import com.andesretail.stock.application.dto.StockQueryCommand
import com.andesretail.stock.application.dto.StockQueryResult
import com.andesretail.stock.application.dto.StockResponseDto
import com.andesretail.stock.application.port.GetStockUseCase
import com.andesretail.stock.domain.exception.ErpStockUnavailableException
import com.andesretail.stock.domain.model.SKU
import com.andesretail.stock.domain.model.StockSource
import com.andesretail.stock.domain.model.WarehouseId
import com.andesretail.stock.web.security.SecurityConfig
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(controllers = [StockController::class])
@Import(SecurityConfig::class, StockControllerTest.TestJwtDecoderConfig::class)
class StockControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockBean
    private lateinit var getStockUseCase: GetStockUseCase

    /**
     * Decodificador JWT de prueba con clave RSA local: evita que el contexto
     * intente resolver por red el JWKS del proveedor de identidad.
     */
    @TestConfiguration
    class TestJwtDecoderConfig {
        @Bean
        fun jwtDecoder(): JwtDecoder {
            val rsaKey: RSAKey = RSAKeyGenerator(2048).generate()
            return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build()
        }
    }

    private fun sampleResult(source: StockSource = StockSource.ERP) = StockQueryResult(
        stock = StockResponseDto(
            sku = "ABC-123",
            warehouseId = "W01",
            available = 90,
            reserved = 10,
            lastUpdated = Instant.parse("2026-10-18T00:00:00Z"),
            cacheHit = source != StockSource.ERP,
        ),
        source = source,
    )

    private val readScope = listOf(SimpleGrantedAuthority("SCOPE_inventory:read"))

    @Test
    fun `responde el cuerpo de C-01 y publica X-Stock-Source`() {
        // Stub con valores exactos: Mockito `any()` devuelve null y el tipo de
        // parámetro Kotlin no nulo lo rechaza en el punto de llamada.
        doReturn(sampleResult())
            .`when`(getStockUseCase)
            .execute(StockQueryCommand(sku = SKU("ABC-123"), warehouseId = WarehouseId("W01")))

        mockMvc.perform(get("/v1/inventory/stock/ABC-123").with(jwt().authorities(readScope)))
            .andExpect(status().isOk)
            .andExpect(header().string(StockController.HEADER_STOCK_SOURCE, "erp"))
            .andExpect(jsonPath("$.sku").value("ABC-123"))
            .andExpect(jsonPath("$.warehouseId").value("W01"))
            .andExpect(jsonPath("$.available").value(90))
            .andExpect(jsonPath("$.reserved").value(10))
            .andExpect(jsonPath("$.lastUpdated").value("2026-10-18T00:00:00Z"))
            .andExpect(jsonPath("$.cacheHit").value(false))
    }

    @Test
    fun `marca la contingencia con stale-cache`() {
        doReturn(sampleResult(StockSource.STALE_CACHE))
            .`when`(getStockUseCase)
            .execute(StockQueryCommand(sku = SKU("ABC-123"), warehouseId = WarehouseId("W01")))

        mockMvc.perform(get("/v1/inventory/stock/ABC-123").with(jwt().authorities(readScope)))
            .andExpect(status().isOk)
            .andExpect(header().string(StockController.HEADER_STOCK_SOURCE, "stale-cache"))
            .andExpect(jsonPath("$.cacheHit").value(true))
    }

    @Test
    fun `devuelve 503 con Retry-After y problem+json cuando el ERP no responde`() {
        doThrow(ErpStockUnavailableException("SAP down"))
            .`when`(getStockUseCase)
            .execute(StockQueryCommand(sku = SKU("ABC-123"), warehouseId = WarehouseId("W01")))

        mockMvc.perform(get("/v1/inventory/stock/ABC-123").with(jwt().authorities(readScope)))
            .andExpect(status().isServiceUnavailable)
            .andExpect(header().string("Retry-After", "5"))
            .andExpect(header().string("Content-Type", "application/problem+json"))
            .andExpect(jsonPath("$.type").value("urn:andes:error:stock-unavailable"))
            .andExpect(jsonPath("$.instance").value("/v1/inventory/stock/ABC-123"))
    }

    @Test
    fun `rechaza 403 sin el scope del contrato`() {
        mockMvc.perform(
            get("/v1/inventory/stock/ABC-123")
                .with(jwt().authorities(listOf(SimpleGrantedAuthority("SCOPE_other")))),
        )
            .andExpect(status().isForbidden)
            .andExpect(jsonPath("$.type").value("urn:andes:error:forbidden"))
    }

    @Test
    fun `rechaza 401 sin autenticacion con problem+json`() {
        mockMvc.perform(get("/v1/inventory/stock/ABC-123"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.type").value("urn:andes:error:unauthorized"))
    }
}
