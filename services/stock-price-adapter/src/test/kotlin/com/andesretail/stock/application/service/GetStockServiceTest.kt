package com.andesretail.stock.application.service

import com.andesretail.stock.application.dto.StockQueryCommand
import com.andesretail.stock.domain.model.SKU
import com.andesretail.stock.domain.model.StockLevel
import com.andesretail.stock.domain.model.StockSource
import com.andesretail.stock.domain.model.WarehouseId
import com.andesretail.stock.domain.port.ErpStockPort
import com.andesretail.stock.domain.port.StockCachePort
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class GetStockServiceTest {

    private val cachePort = mockk<StockCachePort>()
    private val erpPort = mockk<ErpStockPort>()
    private val service = GetStockService(cachePort, erpPort)

    @Test
    fun `cache hit retorna datos`() {
        val level = StockLevel(
            sku = SKU("ABC-123"),
            warehouseId = WarehouseId("W01"),
            quantityOnHand = 100,
            quantityReserved = 10,
            source = StockSource.CACHE,
            timestamp = Instant.parse("2026-10-18T00:00:00Z"),
            etag = "e1",
        )
        every { cachePort.find(SKU("ABC-123"), WarehouseId("W01")) } returns level

        val result = service.execute(StockQueryCommand(SKU("ABC-123"), WarehouseId("W01")))

        assertThat(result.quantityAvailable).isEqualTo(90)
        assertThat(result.source).isEqualTo(StockSource.CACHE)
        verify(exactly = 0) { erpPort.find(any(), any()) }
    }

    @Test
    fun `cache miss consulta ERP y guarda en cache`() {
        every { cachePort.find(SKU("ABC-123"), WarehouseId("W01")) } returns null
        val level = StockLevel(
            sku = SKU("ABC-123"),
            warehouseId = WarehouseId("W01"),
            quantityOnHand = 50,
            quantityReserved = 0,
            source = StockSource.ERP,
            timestamp = Instant.parse("2026-10-18T00:00:00Z"),
            etag = "e2",
        )
        every { erpPort.find(SKU("ABC-123"), WarehouseId("W01")) } returns level
        justRun { cachePort.save(level) }

        val result = service.execute(StockQueryCommand(SKU("ABC-123"), WarehouseId("W01")))

        assertThat(result.source).isEqualTo(StockSource.ERP)
        verify { erpPort.find(SKU("ABC-123"), WarehouseId("W01")) }
        verify { cachePort.save(level) }
    }
}
