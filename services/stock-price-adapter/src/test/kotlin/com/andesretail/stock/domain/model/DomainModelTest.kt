package com.andesretail.stock.domain.model

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Instant

class DomainModelTest {

    @Test
    fun `SKU valida no vacio y longitud`() {
        assertThat(SKU("ABC-123").value).isEqualTo("ABC-123")
        assertThatThrownBy { SKU("") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { SKU("X".repeat(51)) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `WarehouseId valida no vacio y longitud`() {
        assertThat(WarehouseId("W01").value).isEqualTo("W01")
        assertThatThrownBy { WarehouseId(" ") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { WarehouseId("W".repeat(51)) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `StockReservation valida cantidad y fechas`() {
        val now = Instant.parse("2026-10-18T00:00:00Z")
        val reservation = StockReservation(
            id = "r1",
            sku = SKU("ABC-123"),
            warehouseId = WarehouseId("W01"),
            quantity = 3,
            status = ReservationStatus.RESERVED,
            createdAt = now,
            updatedAt = now,
        )
        assertThat(reservation.quantity).isEqualTo(3)
        assertThat(reservation.status).isEqualTo(ReservationStatus.RESERVED)

        assertThatThrownBy {
            reservation.copy(quantity = -1)
        }.isInstanceOf(IllegalArgumentException::class.java)

        assertThatThrownBy {
            reservation.copy(updatedAt = now.minusSeconds(60))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `enumeraciones tienen los valores esperados`() {
        assertThat(StockSource.entries.map { it.name }).containsExactly("CACHE", "ERP", "STALE_CACHE")
        assertThat(ReservationStatus.entries.map { it.name })
            .containsExactly("RESERVED", "RELEASED", "CANCELLED")
    }
}
