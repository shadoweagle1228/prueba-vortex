package com.andesretail.stock.domain.model

@JvmInline
value class WarehouseId(val value: String) {
    init {
        require(value.isNotBlank()) { "El identificador de bodega no puede estar vacio" }
        require(value.matches(Regex("^[A-Z0-9_-]{2,30}$"))) {
            "El WarehouseId '$value' no cumple con el formato permitido"
        }
    }

    override fun toString(): String = value
}
