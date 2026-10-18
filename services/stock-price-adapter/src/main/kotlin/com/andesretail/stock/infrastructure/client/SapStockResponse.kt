package com.andesretail.stock.infrastructure.client

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Payload plano devuelto por SAP ODATA (API_STOCK).
 *
 * Los nombres de campo siguen la convención de SAP (`Material`, `Plant`, ...) y
 * son deliberadamente permisivos: se ignoran propiedades desconocidas y se usan
 * valores por defecto para que una variación de esquema no rompa la consulta.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class SapStockResponse(
    @JsonProperty("Material") val material: String = "",
    @JsonProperty("Plant") val plant: String = "",
    @JsonProperty("UnrestrictedStock") val unrestrictedStock: Long = 0,
    @JsonProperty("ReservedStock") val reservedStock: Long = 0,
    @JsonProperty("LastChanged") val lastChanged: String? = null,
)
