package com.andesretail.stock.web.controller

import com.andesretail.stock.application.dto.StockQueryCommand
import com.andesretail.stock.application.dto.StockResponseDto
import com.andesretail.stock.application.port.GetStockUseCase
import com.andesretail.stock.domain.model.SKU
import com.andesretail.stock.domain.model.WarehouseId
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/inventory")
class StockController(
    private val getStockUseCase: GetStockUseCase,
) {

    @GetMapping("/stock/{sku}")
    @PreAuthorize("hasAuthority('SCOPE_stock:read')")
    fun getStock(
        @PathVariable sku: String,
        @RequestParam(name = "warehouseId", required = false, defaultValue = "W01") warehouseId: String,
    ): ResponseEntity<StockResponseDto> {
        val command = StockQueryCommand(SKU(sku), WarehouseId(warehouseId))
        val response = getStockUseCase.execute(command)
        return ResponseEntity.ok(response)
    }
}
