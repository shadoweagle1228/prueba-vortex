package com.andesretail.stock.infrastructure.mapper

import com.andesretail.stock.domain.model.SKU
import com.andesretail.stock.domain.model.StockLevel
import com.andesretail.stock.domain.model.StockSource
import com.andesretail.stock.domain.model.WarehouseId
import com.andesretail.stock.infrastructure.client.SapStockResponse
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant

/**
 * Traductor (ACL) entre el modelo plano de SAP y el dominio.
 *
 * Ninguna estructura de SAP cruza hacia `domain`: aquí se normalizan cantidades,
 * se acota lo reservado a lo disponible y se deriva un ETag estable que los
 * canales pueden usar para cacheo condicional.
 */
@Component
class SapStockMapper {

    fun toDomain(sku: SKU, warehouseId: WarehouseId, payload: SapStockResponse): StockLevel {
        val onHand = payload.unrestrictedStock.coerceAtLeast(0)
        val reserved = payload.reservedStock.coerceAtLeast(0).coerceAtMost(onHand)
        return StockLevel(
            sku = sku,
            warehouseId = warehouseId,
            quantityOnHand = onHand,
            quantityReserved = reserved,
            source = StockSource.ERP,
            timestamp = parseTimestamp(payload.lastChanged),
            etag = buildEtag(sku, warehouseId, onHand, reserved, payload.lastChanged),
        )
    }

    private fun parseTimestamp(raw: String?): Instant = runCatching { Instant.parse(raw) }.getOrElse { Instant.now() }

    private fun buildEtag(sku: SKU, warehouseId: WarehouseId, onHand: Long, reserved: Long, raw: String?): String {
        val material = "${sku.value}|${warehouseId.value}|$onHand|$reserved|${raw.orEmpty()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(material.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }
}
