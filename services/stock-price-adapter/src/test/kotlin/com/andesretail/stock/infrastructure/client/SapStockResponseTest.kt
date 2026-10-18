package com.andesretail.stock.infrastructure.client

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SapStockResponseTest {

    private val objectMapper = ObjectMapper().registerKotlinModule()

    @Test
    fun `deserializa el payload completo`() {
        val json =
            """{"Material":"SKU-1","Plant":"W01","UnrestrictedStock":42,"ReservedStock":2,"LastChanged":"2026-10-18T00:00:00Z"}"""

        val response: SapStockResponse = objectMapper.readValue(json)

        assertThat(response.material).isEqualTo("SKU-1")
        assertThat(response.plant).isEqualTo("W01")
        assertThat(response.unrestrictedStock).isEqualTo(42)
        assertThat(response.reservedStock).isEqualTo(2)
        assertThat(response.lastChanged).isEqualTo("2026-10-18T00:00:00Z")
    }

    @Test
    fun `ignora propiedades desconocidas y usa defaults`() {
        val json = """{"Material":"SKU-1","SomethingNew":"ignored"}"""

        val response: SapStockResponse = objectMapper.readValue(json)

        assertThat(response.material).isEqualTo("SKU-1")
        assertThat(response.plant).isEmpty()
        assertThat(response.unrestrictedStock).isZero()
        assertThat(response.lastChanged).isNull()
    }
}
