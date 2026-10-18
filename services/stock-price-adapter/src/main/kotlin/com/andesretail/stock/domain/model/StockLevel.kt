package com.andesretail.stock.domain.model

import java.time.Instant

data class StockLevel(
    val sku: SKU,
    val warehouseId: WarehouseId,
    val quantityOnHand: Long,
    val quantityReserved: Long,
    val source: StockSource,
    val timestamp: Instant,
    val etag: String,
) {
    init {
        require(quantityOnHand >= 0) { "quantityOnHand must be >= 0" }
        require(quantityReserved >= 0) { "quantityReserved must be >= 0" }
        require(quantityOnHand >= quantityReserved) { "quantityOnHand must be >= quantityReserved" }
        require(etag.isNotBlank()) { "etag cannot be blank" }
        require(timestamp.isAfter(Instant.EPOCH) || timestamp == Instant.EPOCH) { "invalid timestamp" }
    }

    fun availableToPromise(): Long = quantityOnHand - quantityReserved
}
