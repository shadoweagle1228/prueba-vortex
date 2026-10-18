package com.andesretail.stock.domain.port

import com.andesretail.stock.domain.model.SKU
import com.andesretail.stock.domain.model.StockLevel
import com.andesretail.stock.domain.model.WarehouseId

interface ErpStockPort {
    fun find(sku: SKU, warehouseId: WarehouseId): StockLevel
}
