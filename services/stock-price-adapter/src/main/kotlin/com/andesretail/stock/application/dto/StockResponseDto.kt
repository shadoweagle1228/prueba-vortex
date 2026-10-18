package com.andesretail.stock.application.dto

import java.time.Instant

/**
 * Cuerpo de respuesta del contrato C-01 (`GET /v1/inventory/stock/{sku}`).
 *
 * El contrato exige `sku`, `available`, `reserved`, `lastUpdated` y `cacheHit`
 * en el objeto de respuesta; `warehouseId` es opcional. El origen del dato
 * (`X-Stock-Source`) viaja como cabecera, no en el cuerpo.
 */
data class StockResponseDto(
    val sku: String,
    val warehouseId: String?,
    val available: Long,
    val reserved: Long,
    val lastUpdated: Instant,
    val cacheHit: Boolean,
)
