## Sources

- [observability-requirements.md] Requerimientos de observabilidad y métricas.
- [NFR-08] Correlation ID end-to-end.
- [CA-01] Sanitización de logs.

---

# Diseño de Observabilidad — `skeleton-stock-query`

Este documento describe la arquitectura de telemetría, el pipeline de recolección de métricas Prometheus y el diseño del filtro MDC para trazabilidad distribuida.

---

## 1. Filtro de Correlación y Propagación de MDC (≤15 líneas)

```kotlin
// Ilustrativo (≤15 líneas): Filtro HTTP para extracción y seteo de X-Correlation-ID
@Component
class CorrelationIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(req: HttpServletRequest, res: HttpServletResponse, chain: FilterChain) {
        val correlationId = req.getHeader("X-Correlation-ID") ?: UUID.randomUUID().toString()
        MDC.put("correlationId", correlationId)
        res.setHeader("X-Correlation-ID", correlationId)
        try { chain.doFilter(req, res) } finally { MDC.remove("correlationId") }
    }
}
```

---

## 2. Configuración de Logback Estructurado (JSON)

Se utiliza `logstash-logback-encoder` para emitir eventos en JSON plano a `stdout`:

```xml
<!-- Ilustrativo: Formato JSON de Logback -->
<appender name="JSON_STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LoggingEventCompositeJsonEncoder">
        <providers>
            <timestamp><timeZone>UTC</timeZone></timestamp>
            <logLevel/>
            <mdc/> <!-- Incluye correlationId automáticamente -->
            <message/>
            <throwable/>
        </providers>
    </encoder>
</appender>
```

---

## 3. Exportación de Métricas hacia Prometheus y CloudWatch

- **Endpoint de Scraping:** Expuesto en `/actuator/prometheus` protegido para acceso exclusivo del agente Prometheus de EKS.
- **Instrumentación de Negocio:**
  - Timer `stock_query_duration_seconds` registrado con tags `source` (`cache`, `erp`, `stale`) en el controlador y caso de uso.
  - Gauge `erp_circuit_breaker_state` actualizado mediante el evento `CircuitBreaker.EventPublisher` de Resilience4j.

---

## Assumptions & Open Questions

- [assumption] Prometheus Operator en EKS utiliza un `PodMonitor` para recolectar métricas del namespace de la aplicación cada 15 segundos.
