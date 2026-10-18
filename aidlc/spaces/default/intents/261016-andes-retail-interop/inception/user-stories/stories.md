## Sources

- [desc] Initial description y requerimientos del brief.
- [scope] Scope document y intent backlog.
- [FR-01] a [FR-12], [NFR-01] a [NFR-14] del requirements.md.
- [CA-01] PCI-DSS + PII.
- [CA-02] Backpressure.
- [CA-03] Clean Architecture.

---

## User Stories — Andes Retail Interoperability Platform

Las historias siguen el formato Given/When/Then (BDD) con criterios de aceptación verificables.
Cada historia es independientemente testable.

---

### Épica 1: Consulta de Inventario y Catálogo

**US-01 — Consultar stock disponible por SKU** [FR-01]
**Como** canal externo autenticado (P-01),
**quiero** consultar el stock disponible de un producto dado su SKU y bodega,
**para** mostrar disponibilidad real al usuario final antes de iniciar una compra.

*Criterios de aceptación:*

**Escenario 1 — Cache hit (camino feliz)**
- **Given** un token JWT válido con scope `inventory:read` y `tenantId=ECOMMERCE`
- **And** el stock del SKU-001 está en cache Redis con valor `available: 145`
- **When** se llama `GET /v1/inventory/stock/SKU-001?warehouseId=WH-BOG-01`
- **Then** la respuesta es `200 OK` con `{available: 145, cacheHit: true}` en ≤ 50ms p95

**Escenario 2 — Cache miss, ERP responde**
- **Given** el stock del SKU-001 NO está en cache
- **And** el ERP on-premise responde en < 250ms
- **When** se llama `GET /v1/inventory/stock/SKU-001`
- **Then** la respuesta es `200 OK` con `{available: 145, cacheHit: false}` en ≤ 300ms p95
- **And** el valor queda cacheado en Redis con TTL de 60s

**Escenario 3 — Circuit Breaker abierto**
- **Given** el Circuit Breaker del ERP está en estado OPEN
- **When** se llama `GET /v1/inventory/stock/SKU-001`
- **Then** la respuesta es `200 OK` con el último valor conocido del cache y header `X-Stock-Source: stale-cache`

**Escenario 4 — Sin autorización**
- **Given** un token JWT sin scope `inventory:read`
- **When** se llama `GET /v1/inventory/stock/SKU-001`
- **Then** la respuesta es `403 Forbidden`

*Trazabilidad:* FR-01, NFR-01, NFR-10, CA-02

---

**US-02 — Consultar precio por SKU y canal** [FR-02]
**Como** canal externo autenticado (P-01),
**quiero** consultar el precio vigente de un producto dado su SKU, canal y segmento de cliente,
**para** mostrar el precio correcto (incluyendo promociones) antes de crear una orden.

*Criterios de aceptación:*

**Escenario 1 — Precio estándar**
- **Given** un token JWT válido con scope `catalog:read` y `tenantId=MARKETPLACE_A`
- **When** se llama `GET /v1/catalog/price/SKU-001?channelId=MARKETPLACE_A&customerSegment=REGULAR`
- **Then** la respuesta es `200 OK` con `{price: 99900, currency: COP, promotionalPrice: null}` en ≤ 300ms p95

**Escenario 2 — Precio promocional activo**
- **Given** hay una promoción activa para SKU-001 en el canal ECOMMERCE
- **When** se llama `GET /v1/catalog/price/SKU-001?channelId=ECOMMERCE`
- **Then** la respuesta incluye `{price: 99900, promotionalPrice: 79900, promotionExpiry: "..."}`

**Escenario 3 — Aislamiento de tenant**
- **Given** un token con `tenantId=MARKETPLACE_A`
- **When** se intenta consultar precio con `channelId=MARKETPLACE_B`
- **Then** la respuesta es `403 Forbidden`

*Trazabilidad:* FR-02, NFR-01, NFR-07, CA-01

---

### Épica 2: Gestión de Órdenes

**US-03 — Crear orden end-to-end** [FR-03]
**Como** canal externo autenticado (P-01),
**quiero** crear una orden especificando ítems, dirección de envío y token de pago,
**para** que AndesRetail procese el fulfillment y confirme la transacción al usuario final.

*Criterios de aceptación:*

**Escenario 1 — Orden exitosa (camino feliz)**
- **Given** un token JWT con scope `orders:write`, un `Idempotency-Key: uuid-1`, ítems válidos, y un `paymentToken: tok_visa_xxx`
- **And** el stock del SKU-001 está disponible (145 unidades) y el pago es autorizado por el PSP
- **When** se llama `POST /v1/orders` con el payload completo
- **Then** la respuesta es `201 Created` con `{orderId, status: CONFIRMED, estimatedDelivery}` en ≤ 2s
- **And** el evento `order.confirmed` se publica en EventBridge
- **And** el log de la transacción NO contiene el `paymentToken` completo ni ningún dato PII en claro

**Escenario 2 — Stock insuficiente**
- **Given** el SKU-001 tiene `available: 1` pero se piden 2 unidades
- **When** se llama `POST /v1/orders`
- **Then** la respuesta es `422 Unprocessable Entity` con `{type: stock-unavailable, detail: "..."}`
- **And** NO se inicia el proceso de pago

**Escenario 3 — Pago rechazado con compensación**
- **Given** el stock fue reservado exitosamente
- **And** el PSP rechaza el pago con código `insufficient_funds`
- **When** se completa el flujo de pago
- **Then** la respuesta es `422` con `{type: payment-declined}`
- **And** la reserva de stock en el ERP es liberada automáticamente en ≤ 5s

*Trazabilidad:* FR-03, FR-06, NFR-02, NFR-05, CA-01, CA-02, CA-03

---

**US-04 — Idempotencia en crear orden** [FR-04]
**Como** canal externo (P-01),
**quiero** que reintentos del mismo request de orden con el mismo `Idempotency-Key` retornen la respuesta original sin crear duplicados,
**para** manejar reintentos de red de forma segura sin cobrar o reservar stock dos veces.

*Criterios de aceptación:*

**Escenario 1 — Reintento exitoso**
- **Given** una orden ya creada con `Idempotency-Key: uuid-1` que retornó `201 Created`
- **When** se envía el mismo request con el mismo `Idempotency-Key: uuid-1`
- **Then** la respuesta es `200 OK` (o `201`) con el mismo `orderId` y header `Idempotency-Replay: true`
- **And** NO se crea una segunda orden ni se reserva stock adicional

**Escenario 2 — Mismo key, payload diferente**
- **Given** una orden creada con `Idempotency-Key: uuid-1` y `items: [SKU-001 x2]`
- **When** se envía un request con el mismo key pero `items: [SKU-002 x1]`
- **Then** la respuesta es `422` con `{type: idempotency-key-mismatch}`

*Trazabilidad:* FR-04, CA-03

---

**US-05 — Consultar estado de orden** [FR-05]
**Como** canal externo autenticado (P-01),
**quiero** consultar el estado actual de una orden por su ID,
**para** informar al usuario final el progreso de su compra en tiempo real.

*Criterios de aceptación:*

**Escenario 1 — Consulta exitosa del propio tenant**
- **Given** una orden `ord-abc123` creada por el tenant `ECOMMERCE`
- **And** un token con `tenantId=ECOMMERCE`
- **When** se llama `GET /v1/orders/ord-abc123`
- **Then** la respuesta es `200 OK` con `{orderId, status, items, estimatedDelivery}`

**Escenario 2 — Aislamiento entre tenants**
- **Given** la orden fue creada por `tenantId=ECOMMERCE`
- **And** se usa un token con `tenantId=MARKETPLACE_A`
- **When** se llama `GET /v1/orders/ord-abc123`
- **Then** la respuesta es `403 Forbidden`

*Trazabilidad:* FR-05, NFR-07, CA-01

---

### Épica 3: Notificaciones y Webhooks

**US-06 — Recibir webhook de orden confirmada** [FR-07]
**Como** canal externo registrado (P-01),
**quiero** recibir una notificación HTTP POST en mi endpoint cuando una orden es confirmada,
**para** actualizar mi sistema sin necesidad de hacer polling a la API de órdenes.

*Criterios de aceptación:*

**Escenario 1 — Entrega exitosa**
- **Given** una orden es confirmada y el evento `order.confirmed` se publica en EventBridge
- **And** el canal tiene un endpoint de webhook registrado con HTTPS
- **When** el Notification Service procesa el evento
- **Then** el canal recibe un `POST` en su endpoint en ≤ 5s con `{eventType: order.confirmed, orderId, timestamp, signature}`
- **And** el campo `signature` es un HMAC-SHA256 del payload usando el secreto del canal

**Escenario 2 — Reintento ante fallo**
- **Given** el endpoint del canal retorna `500` en el primer intento
- **When** el Notification Service reintenta
- **Then** se realizan 3 reintentos con backoff (30s, 60s, 120s)
- **And** si todos fallan, el mensaje va a la DLQ y se activa la alarma CloudWatch en < 2min

*Trazabilidad:* FR-07, FR-10, CA-01

---

**US-07 — Recibir webhook de resultado de pago** [FR-08]
**Como** canal externo registrado (P-01),
**quiero** recibir notificaciones del resultado de pago (aprobado o rechazado),
**para** actualizar el estado del pedido en mi sistema y notificar al usuario final.

*Criterios de aceptación:*

**Escenario 1 — Pago aprobado**
- **Given** el PSP aprueba el pago de la orden `ord-abc123`
- **When** el Payment Service publica `payment.approved` en EventBridge
- **Then** el canal recibe `POST` con `{eventType: payment.approved, orderId, chargeId}` en ≤ 5s

**Escenario 2 — Pago rechazado**
- **Given** el PSP rechaza el pago
- **When** el Payment Service publica `payment.rejected`
- **Then** el canal recibe `POST` con `{eventType: payment.rejected, orderId, declineCode}` en ≤ 5s

*Trazabilidad:* FR-08

---

### Épica 4: Seguridad y Operación

**US-08 — Autenticación y autorización multi-tenant** [FR-11]
**Como** canal externo (P-01),
**quiero** autenticarme con mis propias credenciales y recibir un JWT con mis scopes específicos,
**para** acceder a las APIs de la PIE con los permisos que me corresponden sin interferir con otros canales.

*Criterios de aceptación:*

**Escenario 1 — Autenticación exitosa**
- **Given** credenciales válidas `client_id=marketplace-a, client_secret=***`
- **When** se llama `POST /auth/token` con `grant_type=client_credentials`
- **Then** se retorna un JWT con `{tenantId: MARKETPLACE_A, scope: inventory:read orders:write, exp: now+5min}` en ≤ 500ms

**Escenario 2 — Credenciales inválidas**
- **Given** `client_secret` incorrecto
- **When** se llama `POST /auth/token`
- **Then** la respuesta es `401 Unauthorized`

**Escenario 3 — Acceso cruzado entre tenants bloqueado**
- **Given** token con `tenantId=MARKETPLACE_A` y `scope=inventory:read`
- **When** se intenta `POST /v1/orders` (requiere `orders:write`)
- **Then** la respuesta es `403 Forbidden`

*Trazabilidad:* FR-11, NFR-07, CA-01

---

**US-09 — Onboarding de canal externo** [FR-12]
**Como** operador de plataforma (P-02),
**quiero** registrar un nuevo canal externo con sus credenciales, scopes y rate limits,
**para** habilitar su acceso a las APIs en < 4h de trabajo sin cambios de código.

*Criterios de aceptación:*

**Escenario 1 — Onboarding exitoso**
- **Given** se siguen los pasos del runbook de onboarding (crear client en Keycloak, configurar rate limit en Kong, registrar webhook endpoint)
- **When** el proceso se completa
- **Then** el canal puede autenticarse y llamar las APIs en < 4h de trabajo
- **And** el rate limit del canal nuevo no afecta a canales existentes
- **And** ningún cambio de código fue necesario en los servicios de dominio

*Trazabilidad:* FR-12, NFR-13

---

**US-10 — Enmascaramiento de PII en logs** [NFR-05]
**Como** operador de plataforma (P-02),
**quiero** garantizar que ningún dato personal (PII) ni token de pago aparezca en claro en los logs,
**para** cumplir con la Ley 1581/2012 de Colombia y los requerimientos PCI-DSS.

*Criterios de aceptación:*

**Escenario 1 — Verificación automática en CI**
- **Given** un desarrollador agrega `log.info("email: $email")` en el Order Service
- **When** el pipeline de CI ejecuta el scan de PII (Semgrep)
- **Then** el build falla con un mensaje indicando el patrón PII detectado

**Escenario 2 — Verificación en runtime**
- **Given** una orden es creada con email `cliente@test.com` y dirección `Calle 123`
- **When** se busca en los logs de CloudWatch con el filtro `cliente@test.com`
- **Then** NO se encuentran coincidencias — el email está enmascarado o ausente

*Trazabilidad:* NFR-05, CA-01, FR-01

---

## Assumptions & Open Questions

- [assumption] US-09 (onboarding de canal) se verifica en staging antes del primer go-live en producción. El runbook operacional detallado se produce en el stage de NFR Design.
- [assumption] US-10 (PII en logs) se implementa desde el Bloque S (Seguridad) del backlog — sprint 2. No es una feature opcional ni un hardening posterior.
- [assumption] Las historias de webhooks de fulfillment (shipment.started, delivery.confirmed) se escriben en el sprint de mes 3 cuando el WMS Adapter esté en scope.
