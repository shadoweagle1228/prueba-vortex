package com.andesretail.stock.domain.model

data class WarehouseId(val value: String) {
    init {
        require(value.isNotBlank()) { "warehouseId cannot be blank" }
        require(value.length <= 50) { "warehouseId too long" }
    }
}
