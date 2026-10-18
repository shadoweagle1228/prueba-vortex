package com.andesretail.stock.application.dto

import com.andesretail.stock.domain.model.StockSource
import java.time.Instant

data class StockResponseDto(
    val sku: String,
    val warehouseId: String,
    val quantityOnHand: Long,
    val quantityReserved: Long,
    val quantityAvailable: Long,
    val source: StockSource,
    val timestamp: Instant,
    val etag: String,
)
