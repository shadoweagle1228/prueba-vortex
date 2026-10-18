package com.andesretail.stock.domain.exception

/**
 * Señala que el inventario no pudo obtenerse del sistema de origen (ERP/SAP).
 *
 * Vive en el dominio porque es un desenlace del negocio —el inventario no está
 * disponible— y tanto la capa de aplicación (que decide la degradación a caché
 * obsoleta) como la de presentación necesitan reconocerlo sin depender de la
 * infraestructura.
 */
class ErpStockUnavailableException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
