package com.andesretail.stock.application.port

import com.andesretail.stock.application.dto.StockQueryCommand
import com.andesretail.stock.application.dto.StockResponseDto

interface GetStockUseCase {
    fun execute(command: StockQueryCommand): StockResponseDto
}
