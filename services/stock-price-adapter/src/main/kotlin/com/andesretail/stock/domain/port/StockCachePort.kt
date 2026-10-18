package com.andesretail.stock.domain.port

import com.andesretail.stock.domain.model.SKU
import com.andesretail.stock.domain.model.StockLevel
import com.andesretail.stock.domain.model.WarehouseId

/**
 * Caché de niveles de stock con dos horizontes de vigencia.
 *
 * El valor *fresco* sólo es apto para servir una consulta normal (BR-STK-01:
 * antigüedad ≤ 60 s). El valor *obsoleto* conserva el último dato conocido y
 * sólo se sirve como contingencia cuando el ERP no responde (BR-STK-03). La
 * lectura de frescos y obsoletos se separa a propósito: un acierto de caché
 * obsoleta jamás debe presentarse como lectura fresca.
 */
interface StockCachePort {
    fun findFresh(sku: SKU, warehouseId: WarehouseId): StockLevel?

    fun findStale(sku: SKU, warehouseId: WarehouseId): StockLevel?

    fun save(stockLevel: StockLevel)
}
