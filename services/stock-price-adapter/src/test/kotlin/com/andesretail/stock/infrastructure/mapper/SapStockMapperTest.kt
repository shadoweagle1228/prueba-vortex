package com.andesretail.stock.infrastructure.mapper

import com.andesretail.stock.domain.model.SKU
import com.andesretail.stock.domain.model.StockSource
import com.andesretail.stock.domain.model.WarehouseId
import com.andesretail.stock.infrastructure.client.SapStockResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class SapStockMapperTest {

    private val mapper = SapStockMapper()

    @Test
    fun `mapea payload de SAP al dominio`() {
        val payload = SapStockResponse(
            material = "SKU-1",
            plant = "W01",
            unrestrictedStock = 100,
            reservedStock = 10,
            lastChanged = "2026-10-18T00:00:00Z",
        )

        val level = mapper.toDomain(SKU("SKU-1"), WarehouseId("W01"), payload)

        assertThat(level.quantityOnHand).isEqualTo(100)
        assertThat(level.quantityReserved).isEqualTo(10)
        assertThat(level.availableToPromise()).isEqualTo(90)
        assertThat(level.source).isEqualTo(StockSource.ERP)
        assertThat(level.timestamp).isEqualTo(Instant.parse("2026-10-18T00:00:00Z"))
        assertThat(level.etag).hasSize(32)
    }

    @Test
    fun `normaliza cantidades negativas y acota reservado`() {
        val level = SapStockMapper().toDomain(
            SKU("SKU-1"),
            WarehouseId("W01"),
            SapStockResponse(material = "SKU-1", plant = "W01", unrestrictedStock = -5, reservedStock = 99),
        )

        assertThat(level.quantityOnHand).isEqualTo(0)
        assertThat(level.quantityReserved).isEqualTo(0)
    }

    @Test
    fun `acota reservado a lo disponible`() {
        val level = SapStockMapper().toDomain(
            SKU("SKU-1"),
            WarehouseId("W01"),
            SapStockResponse(unrestrictedStock = 10, reservedStock = 999),
        )

        assertThat(level.quantityOnHand).isEqualTo(10)
        assertThat(level.quantityReserved).isEqualTo(10)
        assertThat(level.availableToPromise()).isEqualTo(0)
    }

    @Test
    fun `etag es estable para el mismo payload`() {
        val payload = SapStockResponse("SKU-1", "W01", 100, 5, "2026-10-18T00:00:00Z")
        val mapper = SapStockMapper()

        val first = mapper.toDomain(SKU("SKU-1"), WarehouseId("W01"), payload)
        val second = mapper.toDomain(SKU("SKU-1"), WarehouseId("W01"), payload)

        assertThat(first.etag).isEqualTo(second.etag)
    }

    @Test
    fun `timestamp invalido usa el instante actual`() {
        val payload = SapStockResponse("SKU-1", "W01", 1, 0, "not-a-date")

        val level = SapStockMapper().toDomain(SKU("SKU-1"), WarehouseId("W01"), payload)

        assertThat(level.timestamp).isBeforeOrEqualTo(java.time.Instant.now())
    }
}
