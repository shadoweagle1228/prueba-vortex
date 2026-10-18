package com.andesretail.stock.web.controller

import com.andesretail.stock.application.dto.StockResponseDto
import com.andesretail.stock.application.port.GetStockUseCase
import com.andesretail.stock.domain.model.StockSource
import com.andesretail.stock.web.security.SecurityConfig
import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.security.test.context.support.WithMockJwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.test.context.TestPropertySource

@WebMvcTest(controllers = [StockController::class])
@Import(SecurityConfig::class)
@TestPropertySource(properties = ["spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8081", "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8081/.well-known/jwks.json"])
class StockControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @MockBean
    lateinit var getStockUseCase: GetStockUseCase

    @Test
    @WithMockJwt(authorities = ["SCOPE_stock:read"])
    fun `retorna stock con scope correcto`() {
        val response = StockResponseDto(
            sku = "ABC-123",
            warehouseId = "W01",
            quantityOnHand = 100,
            quantityReserved = 10,
            quantityAvailable = 90,
            source = StockSource.ERP,
            timestamp = Instant.parse("2026-10-18T00:00:00Z"),
            etag = "e1",
        )
        every { getStockUseCase.execute(any()) } returns response

        mockMvc.perform(get("/v1/inventory/stock/ABC-123"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.sku").value("ABC-123"))
            .andExpect(jsonPath("$.quantityAvailable").value(90))
            .andExpect(jsonPath("$.source").value("ERP"))
    }

    @Test
    @WithMockJwt(authorities = ["SCOPE_wrong"])
    fun `rechaza sin scope correcto`() {
        mockMvc.perform(get("/v1/inventory/stock/ABC-123"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `rechaza sin jwt`() {
        mockMvc.perform(get("/v1/inventory/stock/ABC-123"))
            .andExpect(status().isUnauthorized)
    }
}
