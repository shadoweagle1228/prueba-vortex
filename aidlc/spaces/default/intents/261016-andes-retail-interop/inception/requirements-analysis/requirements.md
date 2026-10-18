## Sources

- [desc] Initial description y requerimientos del brief del cliente.
- [scope] Workflow-selected scope: `feature`.
- [Q4] Del intent: criterios de éxito del MVP.
- [CA-01] Restricción absoluta PCI-DSS + enmascaramiento PII.
- [CA-02] Restricción absoluta backpressure en adaptadores legacy.
- [CA-03] Restricción absoluta Clean Architecture.
- [F-Q1] Regulación Colombia Ley 1581/2012.
- [TF-stack] Kotlin + Spring Boot / Python CDK.

---

## Requerimientos Funcionales

### Dominio: Inventario y Catálogo

**FR-01 — Consultar stock por SKU/tienda/canal** [scope][desc]
Como canal externo autenticado, necesito consultar el stock disponible de un producto dado su SKU, bodega y canal, para mostrar disponibilidad al usuario final antes de iniciar una orden.
- **Criterio de aceptación:** `GET /v1/inventory/stock/{sku}?warehouseId={id}&channelId={id}` con JWT válido y scope `inventory:read` retorna `200 OK` con `{available, reserved, warehouseId, lastUpdated, cacheHit}` en ≤ 300ms p95.
- **Criterio negativo:** JWT sin scope `inventory:read` retorna `403 Forbidden`.
- **Trazabilidad:** [scope] Bloque SP-04, [desc] API externa mínima 1.

**FR-02 — Consultar precio por SKU/canal/cliente** [scope][desc]
Como canal externo autenticado, necesito consultar el precio vigente de un producto dado su SKU, canal y segmento de cliente, para mostrar el precio correcto antes de iniciar una orden.
- **Criterio de aceptación:** `GET /v1/catalog/price/{sku}?channelId={id}&customerSegment={seg}` con JWT válido retorna `200 OK` con `{price, currency, validUntil, promotionalPrice}` en ≤ 300ms p95.
- **Trazabilidad:** [scope] Bloque SP-05, [desc] API externa mínima 1.

**FR-03 — Crear orden** [scope][desc]
Como canal externo autenticado, necesito crear una orden de compra especificando los ítems, dirección de envío y token de pago, para que AndesRetail procese el fulfillment y confirme la transacción.
- **Criterio de aceptación:** `POST /v1/orders` con JWT válido, scope `orders:write`, e `Idempotency-Key` retorna `201 Created` con `{orderId, status: CONFIRMED, estimatedDelivery}` en ≤ 2s end-to-end (incluye reserva de stock y autorización de pago).
- **Criterio de error:** stock insuficiente → `422` con `type: stock-unavailable`; pago rechazado → `422` con `type: payment-declined`.
- **Trazabilidad:** [scope] Bloque O-01, [desc] API externa mínima 2, flujo principal A.

**FR-04 — Idempotencia en crear orden** [scope][desc]
Como canal externo, necesito que una segunda llamada con el mismo `Idempotency-Key` retorne la respuesta original sin crear una orden duplicada, para manejar reintentos de red de forma segura.
- **Criterio de aceptación:** dos requests idénticos con el mismo `Idempotency-Key` en un intervalo de 24h → el segundo retorna `200 OK` (o `201`) con `Idempotency-Replay: true` y la misma respuesta original.
- **Criterio negativo:** mismo `Idempotency-Key` con payload diferente → `422` con `type: idempotency-key-mismatch`.
- **Trazabilidad:** [scope] Bloque O-02, [desc] idempotencia en Create Order.

**FR-05 — Consultar estado de orden** [scope][desc]
Como canal externo autenticado, necesito consultar el estado actual de una orden por su ID, para informar al usuario final sobre el progreso de su compra.
- **Criterio de aceptación:** `GET /v1/orders/{orderId}` con JWT del mismo tenant que creó la orden retorna `200 OK` con `{orderId, status, items, tracking}`. JWT de otro tenant → `403 Forbidden`.
- **Trazabilidad:** [scope] Bloque O-04, [desc] API externa mínima 2.

**FR-06 — Compensación de Saga: liberar stock si el pago falla** [scope][CA-02]
Como sistema, cuando el pago sea rechazado después de una reserva de stock exitosa, debo liberar automáticamente la reserva de stock en el ERP para evitar inconsistencias de inventario.
- **Criterio de aceptación:** simulación de pago rechazado → el stock del SKU vuelve al valor previo a la reserva en el ERP y en el cache Redis en ≤ 5s.
- **Trazabilidad:** [scope] Bloque O-03, [desc] flujo principal A "confirmar o compensar según resultado".

**FR-07 — Webhook: orden confirmada** [scope][desc]
Como canal externo registrado, necesito recibir una notificación HTTP POST en mi endpoint cuando una orden es confirmada, para actualizar mi sistema sin necesidad de polling.
- **Criterio de aceptación:** evento `order.confirmed` publicado en EventBridge → canal recibe POST en su endpoint de webhook en ≤ 5s con payload `{eventType, orderId, status, timestamp, signature}`.
- **Trazabilidad:** [scope] Bloque W-01, [desc] requerimiento de webhooks/eventos.

**FR-08 — Webhook: pago aprobado/rechazado** [scope][desc]
Como canal externo, necesito recibir notificaciones de los resultados de pago para actualizar el estado del pedido en mi sistema.
- **Criterio de aceptación:** eventos `payment.approved` y `payment.rejected` entregados al canal ≤ 5s después del resultado del PSP. Payload incluye `{eventType, orderId, chargeId, declineCode (si rechazado)}`.
- **Trazabilidad:** [scope] Bloque W-02, [desc] webhooks/eventos.

**FR-09 — Webhook: despacho iniciado / entrega confirmada** [scope][desc]
Como canal externo, necesito recibir notificaciones de los eventos de fulfillment para mantener al cliente informado del estado de su envío.
- **Criterio de aceptación:** eventos `shipment.started` y `delivery.confirmed` entregados al canal ≤ 60s después de ser registrados en el WMS. Disponible en mes 3 (WMS Adapter).
- **Trazabilidad:** [scope] Bloque W-04, [desc] webhooks/eventos.

**FR-10 — Reintento de webhook con DLQ** [scope][CA-02]
Como sistema, cuando un endpoint de webhook de un canal no esté disponible, debo reintentar la entrega con backoff exponencial antes de mover el evento a una DLQ.
- **Criterio de aceptación:** 3 reintentos (30s, 60s, 120s) ante fallo del endpoint. Después del 3er fallo → mensaje en DLQ SQS → CloudWatch Alarm activa en < 2min.
- **Trazabilidad:** [scope] Bloque W-03, [desc] resiliencia.

**FR-11 — Autenticación OAuth2/OIDC multi-tenant** [scope][CA-01]
Como canal externo, necesito autenticarme con credenciales propias (client_credentials) para obtener un JWT con mis scopes y límites específicos, diferenciado de otros canales.
- **Criterio de aceptación:** `POST /auth/token` con `client_id` y `client_secret` válidos → JWT con `{tenantId, scope, roles, exp}` en ≤ 500ms. Token de canal A no puede llamar APIs con restricción de canal B.
- **Trazabilidad:** [scope] Bloque S-01/S-02, [desc] seguridad OIDC/OAuth2 + multi-tenant.

**FR-12 — Onboarding de canal externo** [scope][CA-01]
Como operador de la plataforma, necesito registrar un nuevo canal externo con sus credenciales, scopes y rate limits específicos, para habilitar su acceso a las APIs sin afectar a otros canales.
- **Criterio de aceptación:** proceso de onboarding documentado; nuevo canal obtiene credenciales en < 4h de trabajo. Rate limit del canal nuevo no afecta a canales existentes.
- **Trazabilidad:** [scope] Flujo D del user-flow.

---

## Requerimientos No Funcionales

### Rendimiento

**NFR-01 — Latencia p95 para stock/precio** [desc][Q4]
- **Requerimiento:** p95 ≤ 300ms para `GET /v1/inventory/stock/{sku}` y `GET /v1/catalog/price/{sku}` medidos desde el API Gateway hasta la respuesta, bajo carga normal.
- **Criterio de verificación:** load test k6 a 100 req/s durante 5 minutos → p95 ≤ 300ms. 90%+ de requests resueltos desde cache Redis (p95 < 50ms en cache hit).
- **Trazabilidad:** [desc] "Latencia p95 < 300 ms para stock/precio".

**NFR-02 — Latencia end-to-end para crear orden** [desc]
- **Requerimiento:** tiempo total de `POST /v1/orders` (incluyendo reserva de stock + autorización de pago) ≤ 2s p95 en condiciones normales.
- **Criterio de verificación:** load test k6 con flujo completo de orden → p95 ≤ 2s.
- **Trazabilidad:** [scope] criterio de verificación Bloque O-01.

**NFR-03 — Absorción de picos 10x** [desc][Q4]
- **Requerimiento:** la plataforma debe soportar 10x el volumen baseline sin degradar los sistemas legacy (ERP, WMS) ni superar el SLA de latencia.
- **Criterio de verificación:** load test al 10x volumen baseline → rate limiting activo en Kong, Circuit Breaker absorbe el exceso hacia ERP, latencia p95 no supera 2x el SLA normal. Los sistemas legacy no muestran incremento de carga proporcional al tráfico.
- **Trazabilidad:** [desc] "Picos 10x — debe responder rápido y NO tumbar ERP/WMS", [CA-02].

### Disponibilidad

**NFR-04 — Disponibilidad de APIs externas** [desc]
- **Requerimiento:** 99.9% de disponibilidad para `GET /v1/inventory/stock/*`, `GET /v1/catalog/price/*`, `POST /v1/orders`, `GET /v1/orders/*` (máx. 8.76h de downtime/año).
- **Criterio de verificación:** monitoreo externo (CloudWatch Synthetics o similar) con check cada 1 minuto. Alertas activadas cuando la disponibilidad cae bajo 99.9% en una ventana de 30 días.
- **Trazabilidad:** [desc] "Disponibilidad 99.9% para APIs externas".

### Seguridad y Cumplimiento

**NFR-05 — Enmascaramiento de PII en logs** [CA-01][F-Q1]
- **Requerimiento:** ningún campo PII (nombre, email, teléfono, dirección) ni token de pago aparece en claro en ningún log de aplicación en ningún entorno.
- **Criterio de verificación:** test automatizado de log sanitization en CI: un request de creación de orden con datos reales → grep en los logs no encuentra patrones de email, teléfono ni tarjeta. Revisión manual en CloudWatch Insights mensual.
- **Trazabilidad:** [CA-01], [F-Q1] Ley 1581/2012 Colombia Art. 17, [scope] Bloque S-04.

**NFR-06 — Cifrado en tránsito y en reposo** [CA-01]
- **Requerimiento:** TLS 1.3 obligatorio en todos los endpoints externos e internos. Cifrado KMS para RDS Aurora, ElastiCache Redis, SQS y Secrets Manager.
- **Criterio de verificación:** `nmap --script ssl-enum-ciphers` en todos los endpoints → solo TLS 1.3 aceptado. Configuración CDK con `storage_encrypted=True` y `kms_key` explícito en todos los recursos de datos.
- **Trazabilidad:** [CA-01], [desc] "Seguridad inconsistente".

**NFR-07 — Autorización por scopes y por canal (multi-tenant)** [CA-01][desc]
- **Requerimiento:** cada canal tiene scopes específicos. Un canal con scope `inventory:read` no puede crear órdenes. Un canal `tenantId=MARKETPLACE_A` no puede acceder a datos del `tenantId=MARKETPLACE_B`.
- **Criterio de verificación:** tests de seguridad: token de canal A intentando operaciones de canal B → `403 Forbidden` en todos los casos.
- **Trazabilidad:** [desc] "Autorización por scopes/roles + políticas por canal", [Q8 intent].

**NFR-08 — Auditoría end-to-end con correlation-id** [desc]
- **Requerimiento:** 100% de las transacciones llevan un `X-Correlation-ID` único propagado en todos los servicios y registrado en los logs.
- **Criterio de verificación:** log de un request en Order Service muestra el mismo `correlationId` que el log del Stock Adapter para la misma transacción.
- **Trazabilidad:** [desc] "Auditoría end-to-end con correlation-id", [scope] Bloque OB-04.

**NFR-09 — Rotación de secretos** [CA-01]
- **Requerimiento:** todos los secretos (credenciales de base de datos, API keys del ERP, client secrets de Keycloak, API key del PSP) se rotan automáticamente según la política definida (DB: 30 días, ERP API key: 90 días, Keycloak: 60 días).
- **Criterio de verificación:** alarma CloudWatch cuando una rotación falla. Ningún secreto tiene más de 90 días sin rotación.
- **Trazabilidad:** [CA-01], [desc] "Rotación de claves / expiración / estrategia de secretos".

### Resiliencia

**NFR-10 — Circuit Breaker en adaptadores legacy** [CA-02]
- **Requerimiento:** el Stock Adapter implementa un Circuit Breaker (Resilience4j) que se abre cuando la tasa de fallos del ERP supera el 50% en 30 llamadas consecutivas, y retorna el último valor conocido del cache.
- **Criterio de verificación:** ERP simulado offline → Circuit Breaker abre en ≤ 30 llamadas. Las consultas de stock siguen respondiendo con valores del cache. Métrica `erp_circuit_breaker_state` = 1 visible en Grafana.
- **Trazabilidad:** [CA-02], [desc] "Circuit breaker", [scope] Bloque SP-02.

**NFR-11 — Rate limiting por canal** [CA-02][desc]
- **Requerimiento:** cada canal tiene un rate limit configurado en Kong (100 req/s por defecto). Requests que superan el límite reciben `429 Too Many Requests` con header `Retry-After`.
- **Criterio de verificación:** load test: 101 req/s desde el mismo consumer → al menos 1 request recibe 429. Rate limit de un canal no afecta a otro canal.
- **Trazabilidad:** [CA-02], [desc] "Rate limit", [scope] Bloque S-03.

**NFR-12 — Backpressure: proteger ERP de sobrecargas** [CA-02]
- **Requerimiento:** el Stock Adapter tiene un Bulkhead (thread pool aislado, máx. 20 threads) para las llamadas al ERP. Cuando el pool está lleno, las llamadas adicionales fallan rápido y retornan el cache, sin bloquear el Order Service.
- **Criterio de verificación:** 200 requests simultáneos al Stock Adapter → ERP no recibe más de 20 llamadas concurrentes. Order Service mantiene su propio thread pool operacional.
- **Trazabilidad:** [CA-02], [scope] Bloque SP-03.

### Evolución y Mantenibilidad

**NFR-13 — Agregar nuevos canales con mínimo impacto** [desc]
- **Requerimiento:** el onboarding de un nuevo canal externo no requiere cambios de código en los servicios de dominio — solo configuración en Keycloak y Kong.
- **Criterio de verificación:** onboarding de un segundo canal completado en < 4h de trabajo sin despliegue de código nuevo.
- **Trazabilidad:** [desc] "Evolución: agregar nuevos sistemas con mínimo impacto", [scope] Flujo D.

**NFR-14 — Cobertura de tests ≥ 80%** [memory:M1][CA-03]
- **Requerimiento:** todos los servicios Kotlin/Spring Boot mantienen cobertura de líneas ≥ 80% verificada en CI antes de merge.
- **Criterio de verificación:** CI pipeline ejecuta JaCoCo → build falla si la cobertura cae bajo 80%.
- **Trazabilidad:** [memory:M1] prácticas TDD afirmadas en practices-discovery.

---

## Assumptions & Open Questions

- [assumption] FR-09 (webhooks de fulfillment) está condicionado a la disponibilidad del WMS Adapter, que es scope de mes 3. En el MVP solo se implementan FR-07 y FR-08.
- [assumption] NFR-03 (10x picos) se verifica con un load test previo a cada evento promocional planificado, no de forma continua. La infraestructura EKS tiene HPA configurado para escalar automáticamente.
- [assumption] Los IDs FR-N y NFR-N son estables. Si se agregan requerimientos en stages posteriores, se numeran de forma continua sin reutilizar IDs.
