## Sources

- [desc] Initial description del proyecto Andes Retail.
- [FR-01] Consulta de stock por SKU/bodega/canal.
- [CA-02] Backpressure y resiliencia en adaptadores legacy.
- [ADR-03] Anti-Corruption Layer (ACL).
- [ADR-04] Circuit Breaker con Fail-Closed y Cache Stale.
- [C-01, C-09] Contratos de inventario y ERP.

---

# Reglas de Negocio — `skeleton-stock-query`

Este documento compila las reglas de negocio, políticas de resiliencia y validaciones operacionales aplicables a la unidad de consulta de stock y adaptador hacia el ERP.

```yaml
rules:
  - id: BR-STK-01
    name: Prioridad de Caché en Consulta
    description: >
      Toda consulta de stock debe verificar primero la existencia de datos frescos
      en ElastiCache Redis. Si el registro existe y su edad es menor a 60 segundos,
      se retorna directamente sin invocar al ERP on-premise.
    trigger: Invocación de GET /v1/inventory/stock/{sku}
    condition: redis.exists(key) == true AND age <= 60s
    action: Retornar StockLevel con header X-Stock-Source=cache (latencia < 50ms)

  - id: BR-STK-02
    name: Protección por Circuit Breaker (Resilience4j)
    description: >
      Las llamadas al ERP están gobernadas por un Circuit Breaker con ventana deslizante
      de 30 llamadas. Si la tasa de errores o timeouts supera el 50%, el circuito conmuta
      a estado OPEN durante 60 segundos, bloqueando llamadas salientes hacia el ERP.
    trigger: Fallo o timeout (>250ms) en llamada al ERP
    condition: failure_rate >= 50% en 30 llamadas
    action: Transición a OPEN y fallback a Stale Cache

  - id: BR-STK-03
    name: Degradación Controlada con Stale Cache
    description: >
      Cuando el Circuit Breaker esté OPEN o el ERP no responda, el sistema debe consultar
      la copia de contingencia (stale cache) en Redis (TTL de hasta 60 min). Si existe,
      retorna 200 OK con header X-Stock-Source=stale-cache para mantener operatividad.
    trigger: Circuit Breaker OPEN o ERP timeout
    condition: redis.exists(stale_key) == true
    action: Retornar StockLevel con header X-Stock-Source=stale-cache

  - id: BR-STK-04
    name: Fail-Closed ante Agotamiento de Recursos
    description: >
      Si el Circuit Breaker está OPEN y la copia de stale cache tampoco existe o ha expirado,
      el sistema debe responder con HTTP 503 Service Unavailable y cabecera Retry-After: 5,
      evitando responder datos inventados o no verificables.
    trigger: Circuit Breaker OPEN AND stale cache miss
    condition: redis.exists(stale_key) == false
    action: Retornar HTTP 503 con Retry-After=5 y ProblemDetails RFC 7807

  - id: BR-STK-05
    name: Aislamiento por Bulkhead
    description: >
      Las solicitudes concurrentes hacia el ERP on-premise no deben superar en ningún
      momento 20 hilos simultáneos. Solicitudes adicionales encoladas por más de 100ms
      son rechazadas de inmediato hacia el fallback para no saturar al ERP.
    trigger: Alta concurrencia de consultas en cache miss
    condition: concurrent_calls > 20 OR wait_duration > 100ms
    action: Invocar fallback a stale cache inmediatamente

  - id: BR-STK-06
    name: Normalización de Valores Negativos SAP
    description: >
      Cualquier respuesta de SAP con stock negativo o nulo en LABST/UMLMC debe ser
      interpretada y normalizada como 0 unidades disponibles, impidiendo errores aritméticos
      en los canales externos.
    trigger: Mapeo de datos en el Anti-Corruption Layer
    condition: sap_labst < 0 OR sap_labst == null
    action: Asignar available = 0 y registrar advertencia en métricas
```

---

## Assumptions & Open Questions

- [assumption] Las políticas de Circuit Breaker y Bulkhead se implementan utilizando Resilience4j configurado mediante application.yml en Spring Boot.
- [assumption] La tolerancia de tiempo para la respuesta del ERP es de 250ms antes de considerarla timeout para salvaguardar el SLA de 300ms p95 hacia el cliente externo.
