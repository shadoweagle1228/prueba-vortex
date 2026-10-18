package com.andesretail.stock.application.service

import com.andesretail.stock.application.dto.StockQueryCommand
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
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class GetStockServiceTest {

    private val cachePort = mockk<StockCachePort>()
    private val erpPort = mockk<ErpStockPort>()
    private val service = GetStockService(cachePort, erpPort)

    private val sku = SKU("ABC-123")
    private val warehouse = WarehouseId("W01")
    private val command = StockQueryCommand(sku, warehouse)

    private fun level(quantityOnHand: Long = 100, quantityReserved: Long = 10, source: StockSource = StockSource.ERP) = StockLevel(
        sku = sku,
        warehouseId = warehouse,
        quantityOnHand = quantityOnHand,
        quantityReserved = quantityReserved,
        source = source,
        timestamp = Instant.parse("2026-10-18T00:00:00Z"),
        etag = "etag-1",
    )

    @Test
    fun `acierto de cache fresca responde sin invocar al ERP`() {
        every { cachePort.findFresh(sku, warehouse) } returns level(source = StockSource.CACHE)

        val result = service.execute(command)

        assertThat(result.source).isEqualTo(StockSource.CACHE)
        assertThat(result.stock.cacheHit).isTrue()
        assertThat(result.stock.available).isEqualTo(90)
        assertThat(result.stock.reserved).isEqualTo(10)
        verify(exactly = 0) { erpPort.find(any(), any()) }
    }

    @Test
    fun `sin dato fresco consulta el ERP y refresca la cache`() {
        val fromErp = level()
        every { cachePort.findFresh(sku, warehouse) } returns null
        every { erpPort.find(sku, warehouse) } returns fromErp
        justRun { cachePort.save(fromErp) }

        val result = service.execute(command)

        assertThat(result.source).isEqualTo(StockSource.ERP)
        assertThat(result.stock.cacheHit).isFalse()
        assertThat(result.stock.available).isEqualTo(90)
        verify(exactly = 1) { cachePort.save(fromErp) }
    }

    @Test
    fun `un valor obsoleto presente no se sirve si el ERP responde`() {
        val fromErp = level(quantityOnHand = 7, quantityReserved = 0)
        every { cachePort.findFresh(sku, warehouse) } returns null
        every { erpPort.find(sku, warehouse) } returns fromErp
        justRun { cachePort.save(fromErp) }

        val result = service.execute(command)

        // BR-STK-01: la caché obsoleta sólo entra como contingencia, nunca como lectura normal.
        assertThat(result.source).isEqualTo(StockSource.ERP)
        assertThat(result.stock.available).isEqualTo(7)
        verify(exactly = 0) { cachePort.findStale(any(), any()) }
    }

    @Test
    fun `si el ERP cae se sirve el ultimo valor conocido como stale-cache`() {
        every { cachePort.findFresh(sku, warehouse) } returns null
        every { erpPort.find(sku, warehouse) } throws ErpStockUnavailableException("SAP down")
        every { cachePort.findStale(sku, warehouse) } returns level(
            quantityOnHand = 42,
            quantityReserved = 2,
            source = StockSource.STALE_CACHE,
        )

        val result = service.execute(command)

        assertThat(result.source).isEqualTo(StockSource.STALE_CACHE)
        assertThat(result.stock.cacheHit).isTrue()
        assertThat(result.stock.available).isEqualTo(40)
    }

    @Test
    fun `fail-closed si el ERP cae y no hay valor obsoleto`() {
        every { cachePort.findFresh(sku, warehouse) } returns null
        every { erpPort.find(sku, warehouse) } throws ErpStockUnavailableException("SAP down")
        every { cachePort.findStale(sku, warehouse) } returns null

        assertThatThrownBy { service.execute(command) }
            .isInstanceOf(ErpStockUnavailableException::class.java)
    }

    @Test
    fun `si la lectura de la cache obsoleta falla se propaga el 503`() {
        every { cachePort.findFresh(sku, warehouse) } returns null
        every { erpPort.find(sku, warehouse) } throws ErpStockUnavailableException("SAP down")
        every { cachePort.findStale(sku, warehouse) } throws IllegalStateException("redis down")

        assertThatThrownBy { service.execute(command) }
            .isInstanceOf(ErpStockUnavailableException::class.java)
    }
}
