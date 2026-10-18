## Sources

- [reliability-requirements.md] Requerimientos de confiabilidad y resiliencia.
- [CA-02] Restricción absoluta backpressure en adaptadores legacy.
- [rules.md] Reglas BR-STK-01 a BR-STK-06.

---

# Diseño de Confiabilidad y Resiliencia — `skeleton-stock-query`

Este documento describe la arquitectura de tolerancia a fallos, la configuración declarativa de **Resilience4j** y el mecanismo de degradación controlada hacia *stale cache*.

---

## 1. Configuración Declarativa de Resilience4j (`application.yml`)

```yaml
resilience4j:
  circuitbreaker:
    instances:
      erpStock:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 30
        minimumNumberOfCalls: 10
        failureRateThreshold: 50.0
        waitDurationInOpenState: 60s
        permittedNumberOfCallsInHalfOpenState: 5
        automaticTransitionFromOpenToHalfOpenEnabled: true
  bulkhead:
    instances:
      erpStock:
        maxConcurrentCalls: 20
        maxWaitDuration: 100ms
```

---

## 2. Implementación del Patrón Fallback y Degradación

El adaptador `SapStockAdapter` decora la llamada hacia el ERP con el Circuit Breaker y el Bulkhead. Si se produce una falla, timeout o si el circuito se encuentra abierto, se ejecuta el método de contingencia:

```kotlin
// Ilustrativo (≤15 líneas): Adaptador con CircuitBreaker y Fallback a Stale Cache
@CircuitBreaker(name = "erpStock", fallbackMethod = "fallbackStock")
@Bulkhead(name = "erpStock")
override fun queryStockFromErp(sku: SKU, warehouseId: WarehouseId): StockLevel {
    return erpClient.fetchStock(sku.value, warehouseId.value)
}

fun fallbackStock(sku: SKU, warehouseId: WarehouseId, ex: Throwable): StockLevel {
    log.warn("ERP no disponible ($ex). Activando fallback stale cache para SKU: ${sku.value}")
    return cachePort.getStale(sku, warehouseId) 
        ?: throw ErpUnavailableException("Stock no disponible en caché ni en ERP")
}
```

---

## 3. Manejo de Excepciones y Conversión a RFC 7807

Cuando `ErpUnavailableException` es lanzada (fallo del ERP y no existe copia en caché):
- El `GlobalExceptionHandler` captura la excepción.
- Retorna código HTTP `503 Service Unavailable`.
- Añade la cabecera `Retry-After: 5`.
- Emite el cuerpo JSON conforme a RFC 7807 (`ProblemDetails`) con `type: "urn:andes:error:erp-unavailable"` y el `correlationId` correspondiente.

---

## Assumptions & Open Questions

- [assumption] Las transiciones de estado del Circuit Breaker emiten eventos internos capturados por un listener que actualiza la métrica `erp_circuit_breaker_state` para Prometheus.
