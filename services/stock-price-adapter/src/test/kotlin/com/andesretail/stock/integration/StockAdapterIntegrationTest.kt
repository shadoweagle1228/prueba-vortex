package com.andesretail.stock.integration

import com.andesretail.stock.application.dto.StockQueryCommand
import com.andesretail.stock.application.service.GetStockService
import com.andesretail.stock.domain.exception.ErpStockUnavailableException
import com.andesretail.stock.domain.model.SKU
import com.andesretail.stock.domain.model.StockLevel
import com.andesretail.stock.domain.model.StockSource
import com.andesretail.stock.domain.model.WarehouseId
import com.andesretail.stock.domain.port.ErpStockPort
import com.andesretail.stock.domain.port.StockCachePort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class StockAdapterIntegrationTest {

    private val cachePort = mockk<StockCachePort>()
    private val erpPort = mockk<ErpStockPort>()
    private val service = GetStockService(cachePort, erpPort)

    private val sku = SKU("ABC-123")
    private val warehouse = WarehouseId("W01")
    private val command = StockQueryCommand(sku, warehouse)

    private fun level(quantityOnHand: Long, quantityReserved: Long, source: StockSource) = StockLevel(
        sku = sku,
        warehouseId = warehouse,
        quantityOnHand = quantityOnHand,
        quantityReserved = quantityReserved,
        source = source,
        timestamp = Instant.parse("2026-10-18T00:00:00Z"),
        etag = "skeleton-e1",
    )

    @Test
    fun `skeleton end-to-end cache miss then ERP`() {
        val fromErp = level(100, 0, StockSource.ERP)
        every { cachePort.findFresh(sku, warehouse) } returns null
        every { erpPort.find(sku, warehouse) } returns fromErp
        justRun { cachePort.save(fromErp) }

        val result = service.execute(command)

        assertThat(result.stock.available).isEqualTo(100)
        assertThat(result.stock.sku).isEqualTo("ABC-123")
        assertThat(result.source).isEqualTo(StockSource.ERP)
    }

    @Test
    fun `skeleton end-to-end ERP caido sirve contingencia obsoleta`() {
        every { cachePort.findFresh(sku, warehouse) } returns null
        every { erpPort.find(sku, warehouse) } throws ErpStockUnavailableException("SAP down")
        every { cachePort.findStale(sku, warehouse) } returns level(30, 5, StockSource.STALE_CACHE)

        val result = service.execute(command)

        assertThat(result.stock.available).isEqualTo(25)
        assertThat(result.source).isEqualTo(StockSource.STALE_CACHE)
    }
}
