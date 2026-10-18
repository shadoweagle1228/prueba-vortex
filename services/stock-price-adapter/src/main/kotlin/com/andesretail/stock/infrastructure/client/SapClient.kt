package com.andesretail.stock.infrastructure.client

import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@Component
class SapClient(
    private val webClientBuilder: WebClient.Builder,
) {
    private val webClient: WebClient = webClientBuilder.build()
}
