package com.andesretail.stock.infrastructure.cache

import com.andesretail.stock.domain.model.SKU
import com.andesretail.stock.domain.model.StockLevel
import com.andesretail.stock.domain.model.WarehouseId
import com.andesretail.stock.domain.port.StockCachePort
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component

@Component
class RedisStockAdapter(
    private val redisTemplate: StringRedisTemplate,
) : StockCachePort {

    override fun find(sku: SKU, warehouseId: WarehouseId): StockLevel? {
        return null
    }

    override fun save(stockLevel: StockLevel) {
    }
}
