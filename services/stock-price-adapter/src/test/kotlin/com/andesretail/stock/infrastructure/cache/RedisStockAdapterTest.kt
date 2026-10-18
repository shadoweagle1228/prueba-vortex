package com.andesretail.stock.infrastructure.cache

import com.andesretail.stock.domain.model.SKU
import com.andesretail.stock.domain.model.StockLevel
import com.andesretail.stock.domain.model.StockSource
import com.andesretail.stock.domain.model.WarehouseId
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import java.time.Instant

class RedisStockAdapterTest {

    private val redisTemplate = mockk<StringRedisTemplate>()
    private val valueOps = mockk<ValueOperations<String, String>>()
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val adapter = RedisStockAdapter(redisTemplate, objectMapper, 60, 3600)

    private val level = StockLevel(
        sku = SKU("ABC-123"),
        warehouseId = WarehouseId("W01"),
        quantityOnHand = 100,
        quantityReserved = 10,
        source = StockSource.ERP,
        timestamp = Instant.parse("2026-10-18T00:00:00Z"),
        etag = "etag-1",
    )

    private fun payloadJson() = objectMapper.writeValueAsString(
        mapOf(
            "sku" to "ABC-123",
            "warehouseId" to "W01",
            "quantityOnHand" to 100,
            "quantityReserved" to 10,
            "source" to "ERP",
            "timestamp" to "2026-10-18T00:00:00Z",
            "etag" to "etag-1",
        ),
    )

    init {
        every { redisTemplate.opsForValue() } returns valueOps
    }

    @Test
    fun `findFresh lee la clave fresca y marca origen CACHE`() {
        every { valueOps.get("stock:fresh:ABC-123:W01") } returns payloadJson()

        val result = adapter.findFresh(SKU("ABC-123"), WarehouseId("W01"))

        assertThat(result).isNotNull
        assertThat(result!!.quantityOnHand).isEqualTo(100)
        assertThat(result.source).isEqualTo(StockSource.CACHE)
        assertThat(result.etag).isEqualTo("etag-1")
    }

    @Test
    fun `findFresh nunca devuelve el valor obsoleto`() {
        every { valueOps.get("stock:fresh:ABC-123:W01") } returns null

        val result = adapter.findFresh(SKU("ABC-123"), WarehouseId("W01"))

        assertThat(result).isNull()
        verify(exactly = 0) { valueOps.get("stock:stale:ABC-123:W01") }
    }

    @Test
    fun `findStale lee la clave obsoleta y marca origen STALE_CACHE`() {
        every { valueOps.get("stock:stale:ABC-123:W01") } returns payloadJson()

        val result = adapter.findStale(SKU("ABC-123"), WarehouseId("W01"))

        assertThat(result).isNotNull
        assertThat(result!!.source).isEqualTo(StockSource.STALE_CACHE)
    }

    @Test
    fun `la lectura tolera que Redis este caido`() {
        every { valueOps.get(any()) } throws RuntimeException("redis down")

        assertThat(adapter.findFresh(SKU("ABC-123"), WarehouseId("W01"))).isNull()
        assertThat(adapter.findStale(SKU("ABC-123"), WarehouseId("W01"))).isNull()
    }

    @Test
    fun `save escribe la clave fresca y la obsoleta con sus TTL`() {
        val capturedKeys = mutableListOf<String>()
        val capturedTtls = mutableListOf<Duration>()
        every { valueOps.set(capture(capturedKeys), any(), capture(capturedTtls)) } returns Unit

        adapter.save(level)

        verify(exactly = 2) { valueOps.set(any(), any(), any<Duration>()) }
        assertEquals(
            setOf("stock:fresh:ABC-123:W01", "stock:stale:ABC-123:W01"),
            capturedKeys.toSet(),
        )
        assertEquals(setOf(Duration.ofSeconds(60), Duration.ofSeconds(3600)), capturedTtls.toSet())
    }

    @Test
    fun `save tolera fallos de Redis`() {
        every { valueOps.set(any(), any(), any<Duration>()) } throws RuntimeException("redis down")

        adapter.save(level)

        // La caché es best-effort: save nunca propaga el fallo de Redis.
        verify(atLeast = 1) { valueOps.set(any(), any(), any<Duration>()) }
    }

    @Test
    fun `save ignora payloads que no se pueden serializar`() {
        val broken = StockLevel(
            sku = SKU("ABC-123"),
            warehouseId = WarehouseId("W01"),
            quantityOnHand = 1,
            quantityReserved = 0,
            source = StockSource.ERP,
            timestamp = Instant.parse("2026-10-18T00:00:00Z"),
            etag = "e",
        )
        val failingMapper = mockk<ObjectMapper>()
        every { failingMapper.writeValueAsString(any()) } throws RuntimeException("boom")
        val failing = RedisStockAdapter(redisTemplate, failingMapper, 60, 3600)

        failing.save(broken)

        verify(exactly = 0) { valueOps.set(any(), any(), any<Duration>()) }
    }

    @Test
    fun `la lectura ignora payloads corruptos`() {
        every { valueOps.get("stock:fresh:ABC-123:W01") } returns "{no-es-json"

        assertThat(adapter.findFresh(SKU("ABC-123"), WarehouseId("W01"))).isNull()
    }
}
