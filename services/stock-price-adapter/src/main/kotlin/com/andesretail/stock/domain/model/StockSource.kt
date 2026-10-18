package com.andesretail.stock.domain.model

/**
 * Origen del dato de stock reportado al consumidor.
 *
 * Se expone en la cabecera `X-Stock-Source` del contrato C-01 para que el canal
 * sepa si el dato es fresco (`cache`), si viene del ERP (`erp`) o si es el
 * último valor conocido servido como contingencia (`stale-cache`).
 */
enum class StockSource {
    CACHE,
    ERP,
    STALE_CACHE,
    ;

    /** Valor tal como lo publica el contrato C-01 (`X-Stock-Source`). */
    fun wireName(): String = when (this) {
        CACHE -> "cache"
        ERP -> "erp"
        STALE_CACHE -> "stale-cache"
    }
}
