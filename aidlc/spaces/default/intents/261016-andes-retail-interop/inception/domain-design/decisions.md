## Sources

- [desc] Initial description del proyecto.
- [CA-01] PCI-DSS.
- [CA-02] Backpressure.
- [CA-03] Clean Architecture.
- [TF-stack] Kotlin + Spring Boot.
- [MR-Q3] API Gateway híbrido.
- [MR-Q4] Keycloak OSS.

---

## Architecture Decision Records (ADRs)

---

### ADR-01: Modelo de Dinero en Centavos (Integer)

**Contexto:** Las operaciones de precio y pago requieren aritmética exacta. Usar `Double` o `Float` introduce errores de representación de punto flotante (e.g., `0.1 + 0.2 ≠ 0.3`).

**Decisión:** Representar todos los montos monetarios como `Long` en centavos (COP). La clase `Money(amount: Long, currency: Currency)` es el único tipo monetario en el dominio.

**Alternativas rechazadas:**
- `Double/Float`: errores de punto flotante inaceptables en contexto financiero.
- `BigDecimal`: correcto pero verboso en Kotlin; el overhead no se justifica para monedas sin decimales relevantes como COP.

**Consecuencias:** El frontend y las APIs externas deben convertir (e.g., 99900 → $999.00 COP). Toda la lógica de negocio opera con enteros — no hay redondeo en el dominio.

**Trazabilidad:** [FR-03], [interaction-spec.md]

---

### ADR-02: Outbox Pattern para Publicación Confiable de Eventos

**Contexto:** El Order Service debe publicar eventos a EventBridge (`OrderCreated`) de forma confiable. Si el servicio falla después de confirmar la orden en Aurora pero antes de publicar el evento, se pierde el evento y el fulfillment y las notificaciones no se activan.

**Decisión:** Implementar el Outbox Pattern: guardar el evento en una tabla `outbox_events` en la misma transacción ACID que la orden. Un proceso de polling (Spring Scheduled) lee los eventos pendientes y los publica en EventBridge, marcándolos como publicados.

**Alternativas rechazadas:**
- Publicar directamente a EventBridge en el handler de la API: riesgo de pérdida de evento si el servicio falla entre la escritura en DB y la publicación.
- Kafka con transacciones: introduce Kafka como dependencia operacional; EventBridge es suficiente para el volumen del MVP y es zero-ops.
- CDC (Change Data Capture con Debezium): mayor complejidad operacional sin beneficio adicional en el MVP.

**Consecuencias:** Latencia adicional de ~100ms para la publicación del evento (el poller corre cada 100ms). Garantía at-least-once en la publicación. Los consumidores (WMS Adapter, Notification Service) deben ser idempotentes.

**Trazabilidad:** [FR-03], [FR-07], [CA-03]

---

### ADR-03: Anti-Corruption Layer (ACL) para la Integración con el ERP

**Contexto:** El ERP on-premise tiene su propio modelo de datos (SAP) que no debe contaminar el modelo de dominio interno de la PIE. Un cambio en la estructura del ERP no debe requerir cambios en el Order Service.

**Decisión:** Implementar el Stock Adapter como un Anti-Corruption Layer completo. El ACL traduce entre el modelo del ERP (estructuras SAP/REST propietarias) y el modelo de dominio interno (`StockLevel`, `StockReservation`). El Order Service solo conoce los puertos `StockPort` y `PaymentPort` — nunca las estructuras del ERP.

**Alternativas rechazadas:**
- Integración directa: el Order Service dependería del modelo del ERP. Un cambio en el ERP requeriría cambios en el Order Service.
- Shared Kernel: compartir un modelo con el ERP viola la autonomía de los contextos y crea acoplamiento bidireccional.

**Consecuencias:** El ACL requiere mappers explícitos (ERP → dominio y dominio → ERP). El costo es una capa adicional de traducción; el beneficio es el desacoplamiento total del dominio del ERP.

**Trazabilidad:** [CA-02], [CA-03], [FR-01], [FR-02]

---

### ADR-04: Circuit Breaker con Fail-Closed y Cache Stale como Fallback

**Contexto:** El ERP on-premise puede estar temporalmente no disponible o lento. El sistema debe seguir respondiendo consultas de stock sin degradar la experiencia del canal externo. [CA-02]

**Decisión:** Implementar Circuit Breaker (Resilience4j) con la siguiente estrategia de fallback:
1. ERP disponible → retornar stock del ERP, actualizar cache.
2. ERP lento (>250ms) → retornar stock del cache si existe.
3. Circuit Breaker abierto → retornar último valor conocido del cache con header `X-Stock-Source: stale-cache`.
4. Circuit Breaker abierto + cache expirado → retornar HTTP 503 con `Retry-After: 5`.

El enfoque es "fail-closed con degradación controlada" — el sistema prefiere dar una respuesta aproximada antes de retornar un error, pero solo cuando la aproximación es razonablemente reciente (cache no expirado).

**Alternativas rechazadas:**
- Fail-fast siempre: el 100% de las consultas de stock fallarían cuando el ERP está caído, incluyendo la lectura del cache que no necesita al ERP.
- Cache sin expiración: el stale data puede generar overselling. TTL de 60s es el equilibrio.

**Consecuencias:** Posible overselling si el stock real es 0 pero el cache dice > 0. Mitigado con el outbox pattern: si el stock falla en la reserva en el ERP, la Saga lo compensa. La reconciliación nocturna detecta divergencias.

**Trazabilidad:** [CA-02], [NFR-10], [FR-01]

---

### ADR-05: Idempotencia Dual (Redis + Aurora)

**Contexto:** Los canales externos pueden reintentar requests de creación de orden. Se necesita garantizar que un mismo `Idempotency-Key` produzca exactamente el mismo resultado sin efectos secundarios duplicados. [FR-04]

**Decisión:** Implementar idempotencia en dos capas:
1. **Redis (primera línea, rápida):** Al iniciar el procesamiento, verificar Redis con la clave `idempotency:{sha256(Idempotency-Key)}`. Si existe, retornar la respuesta cacheada inmediatamente.
2. **Aurora (garantía durable):** Al confirmar la orden, insertar en la tabla `idempotency_keys` en la misma transacción que la orden. TTL de 24h (proceso de limpieza nocturno).

Si Redis falla, la capa de Aurora previene la duplicación. Si el proceso falla entre la verificación de Redis y la escritura en Aurora, la Saga de compensación garantiza consistencia.

**Alternativas rechazadas:**
- Solo Redis: Redis es volátil. Una caída de Redis podría permitir duplicados si el proceso ya empezó pero no terminó.
- Solo Aurora: latencia adicional para el 100% de los requests (incluyendo duplicados que se resuelven en Redis en < 5ms).

**Consecuencias:** Latencia adicional mínima para el check de Redis (< 5ms en cache hit). Garantía de idempotencia ante fallos parciales.

**Trazabilidad:** [FR-04], [CA-03]

---

### ADR-06: PaymentToken como Value Object Opaco

**Contexto:** El sistema recibe un token de pago del PSP para autorizar el cobro. El token no es el PAN (número de tarjeta). Sin embargo, si se trata como un string genérico, puede filtrarse en logs o serializaciones. [CA-01]

**Decisión:** Encapsular el token de pago en un `PaymentToken` Value Object con `toString()` que redacta el valor (`tok_***REDACTED***`). El valor real solo es accesible mediante un getter explícito que debe usarse conscientemente en la capa de infrastructure para las llamadas al PSP.

**Consecuencias:** Cualquier serialización accidental del objeto (e.g., en un log estructurado) muestra el valor redactado, no el token real. El desarrollador debe hacer explícito el acceso al valor real.

**Trazabilidad:** [CA-01], [FR-03], [NFR-05]

---

## Assumptions & Open Questions

- [assumption] Los ADRs siguen el formato Context/Decision/Alternatives Rejected/Consequences per la guardrail de inception "Architecture decisions require trade-off analysis".
- [assumption] Los ADRs adicionales que surjan en construction (ej. decisión de librería de retry, estrategia de migración de schema de DB) se añaden como ADR-07+.
