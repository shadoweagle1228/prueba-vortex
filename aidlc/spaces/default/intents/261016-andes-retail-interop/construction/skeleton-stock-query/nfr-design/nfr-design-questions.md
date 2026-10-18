## Sources

- [desc] Initial description del proyecto Andes Retail.
- [performance-requirements.md] Requerimientos de latencia y presupuesto.
- [security-requirements.md] Requerimientos de seguridad, OAuth2 JWT y KMS.
- [scalability-requirements.md] HPA y absorción de picos 10x.
- [reliability-requirements.md] Circuit Breaker, Stale Cache y Bulkhead.
- [observability-requirements.md] Prometheus Micrometer y logs JSON.
- [tech-stack-decisions.md] Kotlin 1.9, Spring Boot 3.2, JDK 21 LTS, Resilience4j.

---

## NFDQ-01. Arquitectura de Caché de Dos Niveles

¿Cómo se implementa a nivel lógico la estrategia de doble clave en Redis para caché fresco y stale cache?

- **Estructura de Claves:**
  - Clave fresca: `stock:{warehouseId}:{sku}` con TTL de 60 segundos. Serialización JSON compacta.
  - Clave stale (contingencia): `stock:stale:{warehouseId}:{sku}` con TTL de 3.600 segundos (1 hora).
- **Mecanismo de Escritura:** En cada lectura exitosa desde el ERP, se actualizan ambas claves de manera paralela o en pipeline con Lettuce.
- **Mecanismo de Lectura:** La clave fresca es consultada en el flujo regular. La clave stale es invocada exclusivamente desde el método de fallback anotado con `@CircuitBreaker` o `@Retry`.

[Answer]: Claves duales en Redis (fresca TTL 60s / stale TTL 1h) actualizadas en pipeline tras cada lectura del ERP.

---

## NFDQ-02. Diseño de Resiliencia con Resilience4j Declarativo

¿Cómo se desacoplan las políticas de resiliencia del código de dominio en Spring Boot?

- Uso de decoradores declarativos `@CircuitBreaker(name = "erpStock", fallbackMethod = "fallbackStock")` y `@Bulkhead(name = "erpStock", type = THREADPOOL)` en la clase adaptadora de infraestructura `SapStockAdapter`.
- Las clases del dominio (`GetStockUseCase`, `StockLevel`) permanecen 100% agnósticas de Resilience4j [CA-03].
- El método `fallbackStock` intercepta excepciones `CallNotPermittedException` (circuito abierto), `BulkheadFullException` y `TimeoutException`.

[Answer]: Decoradores Resilience4j en la capa de infraestructura/adaptadores, preservando el dominio puro y testeable.

---

## NFDQ-03. Filtro de Auditoría y Propagación de Correlation-ID

¿Cómo se garantiza que el `X-Correlation-ID` esté presente en cada hilo y llamada saliente?

- Un `OncePerRequestFilter` en Spring Security extrae la cabecera `X-Correlation-ID` del request entrante (o genera un UUID si no viene presente).
- El ID se coloca en el MDC (`MappedDiagnosticContext`) de SLF4J, garantizando que todo log emitido por Logback lo incluya automáticamente en el JSON.
- Un interceptor de cliente HTTP saliente (para llamadas al ERP o dependencias) inyecta la cabecera `X-Correlation-ID` en las peticiones salientes.

[Answer]: MDC de SLF4J administrado por filtro HTTP perimetral con propagación automática a clientes salientes.

---

## Assumptions & Open Questions

- [assumption] La configuración de Resilience4j se define de forma externa en `application.yml` permitiendo ajustar umbrales sin recompilar la aplicación.
