package com.andesretail.stock.domain.model

@JvmInline
value class SKU(val value: String) {
    init {
        require(value.isNotBlank()) { "El SKU no puede estar vacio" }
        require(value.matches(Regex("^[A-Z0-9_-]{3,50}$"))) {
            "El SKU '$value' no cumple con el formato alfanumerico permitido"
        }
    }

    override fun toString(): String = value
}
