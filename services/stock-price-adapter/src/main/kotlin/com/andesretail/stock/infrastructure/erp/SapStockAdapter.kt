package com.andesretail.stock.infrastructure.erp

import com.andesretail.stock.domain.model.SKU
import com.andesretail.stock.domain.model.StockLevel
import com.andesretail.stock.domain.model.StockSource
import com.andesretail.stock.domain.model.WarehouseId
import com.andesretail.stock.domain.port.ErpStockPort
import io.github.resilience4j.bulkhead.annotation.Bulkhead
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
@CircuitBreaker(name = "sapStockCircuitBreaker")
@Bulkhead(name = "sapStockBulkhead")
class SapStockAdapter : ErpStockPort {

    override fun find(sku: SKU, warehouseId: WarehouseId): StockLevel {
        return StockLevel(
            sku = sku,
            warehouseId = warehouseId,
            quantityOnHand = 0,
            quantityReserved = 0,
            source = StockSource.ERP,
            timestamp = Instant.now(),
            etag = UUID.randomUUID().toString(),
        )
    }
}
