package com.andesretail.stock.domain.model

import java.time.Instant

data class StockReservation(
    val id: String,
    val sku: SKU,
    val warehouseId: WarehouseId,
    val quantity: Long,
    val status: ReservationStatus,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(quantity >= 0) { "quantity must be >= 0" }
        require(createdAt <= updatedAt) { "createdAt must be <= updatedAt" }
    }
}
