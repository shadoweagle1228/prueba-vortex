package com.andesretail.stock.domain.port

import com.andesretail.stock.domain.model.SKU
import com.andesretail.stock.domain.model.StockLevel
import com.andesretail.stock.domain.model.WarehouseId

interface StockCachePort {
    fun find(sku: SKU, warehouseId: WarehouseId): StockLevel?
    fun save(stockLevel: StockLevel)
}
