package com.andesretail.stock

import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import io.github.resilience4j.bulkhead.BulkheadRegistry
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

/**
 * Verifica que el contexto completo arranca y que las sondas de Kubernetes son
 * alcanzables sin credenciales.
 *
 * Sin esta prueba, un `GetStockService` sin estereotipo Spring —o unos matchers
 * de Actuator que no cubren `/actuator/health/readiness`— sólo se descubren en el
 * despliegue, cuando el pod nunca llega a `Ready`.
 */
@SpringBootTest
@AutoConfigureMockMvc
class StockPriceAdapterApplicationTests {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry

    @Autowired
    private lateinit var bulkheadRegistry: BulkheadRegistry

    /**
     * Decodificador JWT local: evita que el contexto resuelva por red el JWKS
     * del proveedor de identidad al arrancar.
     */
    @TestConfiguration
    class TestJwtDecoderConfig {
        @Bean
        fun jwtDecoder(): JwtDecoder {
            val rsaKey: RSAKey = RSAKeyGenerator(2048).generate()
            return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build()
        }
    }

    @Test
    fun `el contexto de la aplicación carga`() {
        // Si GetStockService (o cualquier colaborador) no fuese un bean, este
        // arranque fallaría con NoSuchBeanDefinitionException.
    }

    /**
     * Verifica que la configuración *efectiva* de resiliencia es la del diseño de
     * NFR-10/CA-02: no basta con que `application.yml` la declare, tiene que
     * llegar registrada al `CircuitBreakerRegistry`/`BulkheadRegistry` que usan
     * los aspectos de Resilience4j.
     */
    @Test
    fun `la resiliencia del ERP toma los parámetros del diseño`() {
        val circuitBreaker = circuitBreakerRegistry.circuitBreaker("sapStockCircuitBreaker").circuitBreakerConfig
        assertThat(circuitBreaker.slidingWindowSize).isEqualTo(30)
        assertThat(circuitBreaker.minimumNumberOfCalls).isEqualTo(10)
        assertThat(circuitBreaker.failureRateThreshold).isEqualTo(50f)
        assertThat(circuitBreaker.slowCallDurationThreshold).isEqualTo(Duration.ofMillis(250))
        assertThat(circuitBreaker.slowCallRateThreshold).isEqualTo(50f)
        assertThat(circuitBreaker.waitIntervalFunctionInOpenState.apply(1)).isEqualTo(60_000L)

        val bulkhead = bulkheadRegistry.bulkhead("sapStockBulkhead").bulkheadConfig
        assertThat(bulkhead.maxConcurrentCalls).isEqualTo(20)
        assertThat(bulkhead.maxWaitDuration).isEqualTo(Duration.ofMillis(100))
    }

    @Test
    fun `las sondas de liveness y readiness son publicas`() {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk)
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk)
    }

    @Test
    fun `las metricas exigen el scope metrics read`() {
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized)
    }
}
