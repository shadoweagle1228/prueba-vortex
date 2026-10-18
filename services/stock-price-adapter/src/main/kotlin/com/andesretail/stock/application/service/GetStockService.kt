package com.andesretail.stock.application.service

import com.andesretail.stock.application.dto.StockQueryCommand
import com.andesretail.stock.application.dto.StockResponseDto
import com.andesretail.stock.application.port.GetStockUseCase
import com.andesretail.stock.domain.model.StockLevel
import com.andesretail.stock.domain.model.StockSource
import com.andesretail.stock.domain.port.ErpStockPort
import com.andesretail.stock.domain.port.StockCachePort
import java.time.Instant

class GetStockService(
    private val cachePort: StockCachePort,
    private val erpPort: ErpStockPort,
) : GetStockUseCase {

    override fun execute(command: StockQueryCommand): StockResponseDto {
        val cached = cachePort.find(command.sku, command.warehouseId)
        return if (cached != null) {
            toDto(cached)
        } else {
            try {
                val fresh = erpPort.find(command.sku, command.warehouseId)
                cachePort.save(fresh)
                toDto(fresh)
            } catch (e: Exception) {
                throw e
            }
        }
    }

    private fun toDto(stockLevel: StockLevel): StockResponseDto {
        return StockResponseDto(
            sku = stockLevel.sku.value,
            warehouseId = stockLevel.warehouseId.value,
            quantityOnHand = stockLevel.quantityOnHand,
            quantityReserved = stockLevel.quantityReserved,
            quantityAvailable = stockLevel.availableToPromise(),
            source = stockLevel.source,
            timestamp = stockLevel.timestamp,
            etag = stockLevel.etag,
        )
    }
}
