package com.andesretail.stock.infrastructure.erp

import com.andesretail.stock.domain.exception.ErpStockUnavailableException
import com.andesretail.stock.domain.model.SKU
import com.andesretail.stock.domain.model.StockLevel
import com.andesretail.stock.domain.model.WarehouseId
import com.andesretail.stock.domain.port.ErpStockPort
import com.andesretail.stock.infrastructure.client.SapClient
import com.andesretail.stock.infrastructure.mapper.SapStockMapper
import io.github.resilience4j.bulkhead.annotation.Bulkhead
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import org.springframework.stereotype.Component

/**
 * Implementación del puerto de ERP sobre SAP.
 *
 * La resiliencia se declara en la frontera del adaptador: si SAP cae o va lento,
 * el CircuitBreaker abre y el Bulkhead limita las llamadas concurrentes, de modo
 * que el servicio degrada a caché obsoleta en lugar de agotar el pool de hilos.
 * Cualquier fallo se traduce a [ErpStockUnavailableException], el único desenlace
 * que la capa de aplicación reconoce como "ERP no disponible" (BR-STK-02/04/05).
 */
@Component
class SapStockAdapter(
    private val sapClient: SapClient,
    private val mapper: SapStockMapper,
) : ErpStockPort {

    @CircuitBreaker(name = "sapStockCircuitBreaker", fallbackMethod = "fallback")
    @Bulkhead(name = "sapStockBulkhead")
    override fun find(sku: SKU, warehouseId: WarehouseId): StockLevel {
        val response = sapClient.fetchStock(sku.value, warehouseId.value)
        return mapper.toDomain(sku, warehouseId, response)
    }

    @Suppress("unused")
    private fun fallback(sku: SKU, warehouseId: WarehouseId, ex: Throwable): StockLevel {
        throw ErpStockUnavailableException(
            "SAP no disponible para sku=${sku.value} bodega=${warehouseId.value}: ${ex.message}",
            ex,
        )
    }
}
