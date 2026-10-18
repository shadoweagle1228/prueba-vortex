package com.andesretail.stock.application.dto

import com.andesretail.stock.domain.model.StockSource

/**
 * Resultado de una consulta de stock.
 *
 * El cuerpo viaja en el payload (contrato C-01) y el origen del dato en la
 * cabecera `X-Stock-Source`, por eso ambos se transportan juntos y nunca se
 * mezclan dentro del cuerpo de la respuesta.
 */
data class StockQueryResult(
    val stock: StockResponseDto,
    val source: StockSource,
)
