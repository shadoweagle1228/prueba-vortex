package com.andesretail.stock.infrastructure.erp

import com.andesretail.stock.domain.exception.ErpStockUnavailableException
import com.andesretail.stock.domain.model.SKU
import com.andesretail.stock.domain.model.StockSource
import com.andesretail.stock.domain.model.WarehouseId
import com.andesretail.stock.infrastructure.client.SapClient
import com.andesretail.stock.infrastructure.client.SapClientException
import com.andesretail.stock.infrastructure.client.SapStockResponse
import com.andesretail.stock.infrastructure.mapper.SapStockMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class SapStockAdapterTest {

    private val sapClient = mockk<SapClient>()
    private val adapter = SapStockAdapter(sapClient, SapStockMapper())

    @Test
    fun `consulta SAP y traduce al dominio`() {
        every { sapClient.fetchStock("SKU-1", "W01") } returns SapStockResponse(
            material = "SKU-1",
            plant = "W01",
            unrestrictedStock = 50,
            reservedStock = 5,
            lastChanged = "2026-10-18T00:00:00Z",
        )

        val level = adapter.find(SKU("SKU-1"), WarehouseId("W01"))

        assertThat(level.sku.value).isEqualTo("SKU-1")
        assertThat(level.warehouseId.value).isEqualTo("W01")
        assertThat(level.quantityOnHand).isEqualTo(50)
        assertThat(level.quantityReserved).isEqualTo(5)
        assertThat(level.source).isEqualTo(StockSource.ERP)
        verify(exactly = 1) { sapClient.fetchStock("SKU-1", "W01") }
    }

    @Test
    fun `sin proxy de resiliencia el fallo del cliente SAP se propaga tal cual`() {
        // En una instancia directa (sin proxy de Resilience4j) no hay fallback: se propaga el fallo de transporte.
        every { sapClient.fetchStock("SKU-1", "W01") } throws SapClientException("down")

        assertThatThrownBy { adapter.find(SKU("SKU-1"), WarehouseId("W01")) }
            .isInstanceOf(SapClientException::class.java)
    }

    @Test
    fun `el fallback traduce cualquier fallo del ERP a ErpStockUnavailableException`() {
        // Resilience4j invoca el fallback por reflexión sobre la clase destino (es privado a propósito).
        val fallback = SapStockAdapter::class.java.getDeclaredMethod(
            "fallback",
            SKU::class.java,
            WarehouseId::class.java,
            Throwable::class.java,
        )
        fallback.isAccessible = true

        assertThatThrownBy {
            fallback.invoke(adapter, SKU("SKU-1"), WarehouseId("W01"), SapClientException("down"))
        }
            .hasCauseInstanceOf(ErpStockUnavailableException::class.java)
    }
}
