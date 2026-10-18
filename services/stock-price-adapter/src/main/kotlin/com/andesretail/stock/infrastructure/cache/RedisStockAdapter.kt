package com.andesretail.stock.infrastructure.cache

import com.andesretail.stock.domain.model.SKU
import com.andesretail.stock.domain.model.StockLevel
import com.andesretail.stock.domain.model.StockSource
import com.andesretail.stock.domain.model.WarehouseId
import com.andesretail.stock.domain.port.StockCachePort
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * Adaptador de caché sobre Redis con dos horizontes de vigencia.
 *
 * - `stock:fresh:{sku}:{bodega}` (TTL 60 s): único valor apto para responder una
 *   consulta normal.
 * - `stock:stale:{sku}:{bodega}` (TTL 3600 s): último valor conocido, servido
 *   sólo como contingencia cuando el ERP no responde.
 *
 * La caché es *best-effort*: un fallo de Redis degrada a consulta directa al
 * ERP, nunca rompe la petición.
 */
@Component
class RedisStockAdapter(
    private val redisTemplate: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    @Value("\${stock.cache.fresh-ttl-seconds:60}") private val freshTtlSeconds: Long = 60,
    @Value("\${stock.cache.stale-ttl-seconds:3600}") private val staleTtlSeconds: Long = 3600,
) : StockCachePort {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun findFresh(sku: SKU, warehouseId: WarehouseId): StockLevel? = readSafely(freshKey(sku, warehouseId), StockSource.CACHE)

    override fun findStale(sku: SKU, warehouseId: WarehouseId): StockLevel? =
        readSafely(staleKey(sku, warehouseId), StockSource.STALE_CACHE)

    override fun save(stockLevel: StockLevel) {
        val payload = try {
            objectMapper.writeValueAsString(stockLevel.toCacheModel())
        } catch (ex: Exception) {
            log.warn("No se pudo serializar el stock de {}: {}", stockLevel.sku.value, ex.message)
            return
        }
        setSafely(freshKey(stockLevel.sku, stockLevel.warehouseId), payload, freshTtlSeconds)
        setSafely(staleKey(stockLevel.sku, stockLevel.warehouseId), payload, staleTtlSeconds)
    }

    private fun setSafely(key: String, payload: String, ttlSeconds: Long) {
        try {
            redisTemplate.opsForValue().set(key, payload, Duration.ofSeconds(ttlSeconds))
        } catch (ex: Exception) {
            // La caché es best-effort: un fallo de Redis nunca debe tumbar la consulta.
            log.warn("Escritura en Redis fallida para {}: {}", key, ex.message)
        }
    }

    private fun readSafely(key: String, source: StockSource): StockLevel? = try {
        val raw = redisTemplate.opsForValue().get(key)
        if (raw == null) {
            null
        } else {
            objectMapper.readValue(raw, CachedStockLevel::class.java).toDomain(source)
        }
    } catch (ex: Exception) {
        log.warn("Lectura de Redis fallida para {}: {}", key, ex.message)
        null
    }

    private fun freshKey(sku: SKU, warehouseId: WarehouseId) = "stock:fresh:${sku.value}:${warehouseId.value}"

    private fun staleKey(sku: SKU, warehouseId: WarehouseId) = "stock:stale:${sku.value}:${warehouseId.value}"

    private data class CachedStockLevel(
        val sku: String,
        val warehouseId: String,
        val quantityOnHand: Long,
        val quantityReserved: Long,
        val source: String,
        val timestamp: String,
        val etag: String,
    ) {
        fun toDomain(cacheSource: StockSource) = StockLevel(
            sku = SKU(sku),
            warehouseId = WarehouseId(warehouseId),
            quantityOnHand = quantityOnHand,
            quantityReserved = quantityReserved,
            source = cacheSource,
            timestamp = Instant.parse(timestamp),
            etag = etag,
        )
    }

    private fun StockLevel.toCacheModel() = CachedStockLevel(
        sku = sku.value,
        warehouseId = warehouseId.value,
        quantityOnHand = quantityOnHand,
        quantityReserved = quantityReserved,
        source = source.name,
        timestamp = timestamp.toString(),
        etag = etag,
    )
}
