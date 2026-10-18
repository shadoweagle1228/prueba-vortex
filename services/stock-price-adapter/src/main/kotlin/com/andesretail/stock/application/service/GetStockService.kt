package com.andesretail.stock.application.service

import com.andesretail.stock.application.dto.StockQueryCommand
import com.andesretail.stock.application.dto.StockQueryResult
import com.andesretail.stock.application.dto.StockResponseDto
import com.andesretail.stock.application.port.GetStockUseCase
import com.andesretail.stock.domain.exception.ErpStockUnavailableException
import com.andesretail.stock.domain.model.StockLevel
import com.andesretail.stock.domain.model.StockSource
import com.andesretail.stock.domain.port.ErpStockPort
import com.andesretail.stock.domain.port.StockCachePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Caso de uso de consulta de stock (contrato C-01).
 *
 * Orden de resolución, alineado con BR-STK-01 (prioridad de caché), BR-STK-03
 * (degradación controlada) y BR-STK-04 (fail-closed):
 *
 * 1. Acierto de caché **fresca** (antigüedad ≤ TTL fresco, 60 s) → se responde.
 * 2. Sin dato fresco → se consulta el ERP y se refresca la caché.
 * 3. Si el ERP no responde (circuito abierto, bulkhead lleno o error de SAP) →
 *    se sirve el último valor conocido como `stale-cache`.
 * 4. Si tampoco hay valor obsoleto → el error se propaga (503), nunca se
 *    inventa un inventario disponible.
 *
 * El paso 3 es el único lugar donde se sirve caché obsoleta: un valor de hasta
 * una hora de antigüedad nunca se presenta como lectura fresca.
 */
@Service
class GetStockService(
    private val cachePort: StockCachePort,
    private val erpPort: ErpStockPort,
) : GetStockUseCase {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun execute(command: StockQueryCommand): StockQueryResult {
        cachePort.findFresh(command.sku, command.warehouseId)?.let { fresh ->
            return result(fresh, StockSource.CACHE, cacheHit = true)
        }

        val fromErp = try {
            erpPort.find(command.sku, command.warehouseId)
        } catch (ex: ErpStockUnavailableException) {
            return staleFallback(command, ex)
        }

        cachePort.save(fromErp)
        return result(fromErp, StockSource.ERP, cacheHit = false)
    }

    private fun staleFallback(command: StockQueryCommand, cause: ErpStockUnavailableException): StockQueryResult {
        val stale = try {
            cachePort.findStale(command.sku, command.warehouseId)
        } catch (ex: Exception) {
            log.warn("Lectura de caché obsoleta fallida para {}: {}", command.sku.value, ex.message)
            null
        }
        if (stale == null) {
            // Fail-closed: sin dato alguno se propaga el 503 (BR-STK-04).
            throw cause
        }
        log.warn(
            "ERP no disponible; se sirve stock obsoleto para sku={} bodega={}",
            command.sku.value,
            command.warehouseId.value,
        )
        return result(stale, StockSource.STALE_CACHE, cacheHit = true)
    }

    private fun result(stockLevel: StockLevel, source: StockSource, cacheHit: Boolean): StockQueryResult = StockQueryResult(
        stock = StockResponseDto(
            sku = stockLevel.sku.value,
            warehouseId = stockLevel.warehouseId.value,
            available = stockLevel.availableToPromise(),
            reserved = stockLevel.quantityReserved,
            lastUpdated = stockLevel.timestamp,
            cacheHit = cacheHit,
        ),
        source = source,
    )
}
