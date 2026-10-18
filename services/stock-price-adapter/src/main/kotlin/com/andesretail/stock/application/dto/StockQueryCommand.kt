package com.andesretail.stock.application.dto

import com.andesretail.stock.domain.model.SKU
import com.andesretail.stock.domain.model.WarehouseId

data class StockQueryCommand(
    val sku: SKU,
    val warehouseId: WarehouseId,
)
