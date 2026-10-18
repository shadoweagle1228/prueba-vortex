package com.andesretail.stock.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class StockLevelTest {

    @Test
    fun `stock level disponible`() {
        val level = StockLevel(
            sku = SKU("ABC-123"),
            warehouseId = WarehouseId("W01"),
            quantityOnHand = 100,
            quantityReserved = 25,
            source = StockSource.ERP,
            timestamp = Instant.parse("2026-10-18T00:00:00Z"),
            etag = "e1",
        )
        assertThat(level.availableToPromise()).isEqualTo(75)
        assertThat(level.sku.value).isEqualTo("ABC-123")
        assertThat(level.warehouseId.value).isEqualTo("W01")
        assertThat(level.source).isEqualTo(StockSource.ERP)
    }

    @Test
    fun `no permite negativo onHand`() {
        assertThatThrownBy {
            StockLevel(
                sku = SKU("ABC-123"),
                warehouseId = WarehouseId("W01"),
                quantityOnHand = -1,
                quantityReserved = 0,
                source = StockSource.CACHE,
                timestamp = Instant.parse("2026-10-18T00:00:00Z"),
                etag = "e1",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `no permite reservado mayor que onHand`() {
        assertThatThrownBy {
            StockLevel(
                sku = SKU("ABC-123"),
                warehouseId = WarehouseId("W01"),
                quantityOnHand = 10,
                quantityReserved = 20,
                source = StockSource.CACHE,
                timestamp = Instant.parse("2026-10-18T00:00:00Z"),
                etag = "e1",
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
