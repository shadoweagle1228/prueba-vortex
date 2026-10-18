package com.andesretail.stock

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class StockPriceAdapterApplication

fun main(args: Array<String>) {
    @Suppress("SpreadOperator")
    runApplication<StockPriceAdapterApplication>(*args)
}
