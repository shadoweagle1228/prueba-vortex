package com.andesretail.stock.domain.model

data class SKU(val value: String) {
    init {
        require(value.isNotBlank()) { "sku cannot be blank" }
        require(value.length <= 50) { "sku too long" }
    }
}
