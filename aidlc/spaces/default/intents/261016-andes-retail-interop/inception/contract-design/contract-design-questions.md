## Sources

- [desc] Initial description del proyecto.
- [scope] Scope document y intent backlog.
- [FR-01] a [FR-12], [NFR-01] a [NFR-14] del requirements.md.
- [CA-01] PCI-DSS — segmentación CDE, tokenización, enmascaramiento PII.
- [CA-02] Backpressure en adaptadores legacy.
- [CA-03] Clean Architecture — puertos y adaptadores.
- [BC-ORDER] a [BC-NOTIFICATION] del domain-design components.md.
- [ADR-01] a [ADR-06] del domain-design decisions.md.
- [U1] a [U8] del units-generation units.md.

---

## CD-Q1. Superficie de APIs Públicas y Externas

¿Qué unidades exponen APIs consumidas desde el exterior de la PIE y cuál es su formato y protocolo?

- **Canales Externos (E-commerce, Marketplaces):**
  - `skeleton-stock-query` (U1): `GET /v1/inventory/stock/{sku}`
  - `price-query` (U2): `GET /v1/catalog/price/{sku}`
  - `order-lifecycle` (U3): `POST /v1/orders`, `GET /v1/orders/{orderId}`
  - `security-compliance` (U6 / Keycloak): `POST /auth/token`
- **Protocolo:** REST sobre HTTPS con TLS 1.3 mandatorio [CA-01]. Formato de intercambio JSON estricto UTF-8.
- **Autenticación:** OAuth2 Client Credentials con JWT emitido por Keycloak, validado en Kong Ingress / API Gateway [FR-11].
- **Cabeceras estándar obligatorias:**
  - `Authorization: Bearer <jwt>`
  - `X-Correlation-ID: <uuid>` (propagación de trazabilidad end-to-end [NFR-08])
  - `Idempotency-Key: <uuid>` (obligatorio en `POST /v1/orders` [FR-04])

[Answer]: APIs públicas REST / JSON con especificación OpenAPI 3.0, autenticadas por Bearer JWT y con correlación obligatoria.

---

## CD-Q2. Mecanismos de Integración entre Unidades Internas

¿Cómo se comunican las unidades dentro del cluster EKS de la PIE?

- **Llamadas Síncronas (RPC/REST interno):**
  - `order-lifecycle` (U3) → `skeleton-stock-query` (U1): Reserva y liberación de stock (`StockPort`). Protocolo: HTTP/REST interno en red de pods (Service Discovery de Kubernetes). Timeouts estrictos (connect: 100ms, read: 500ms).
  - `order-lifecycle` (U3) → `payment-authorization` (U4): Autorización de cobro con token (`PaymentPort`). Protocolo: HTTP/REST interno en subred CDE privada aislada [CA-01]. Timeouts: connect: 200ms, read: 1500ms.
- **Comunicación Asíncrona (Event-Driven):**
  - `order-lifecycle` (U3) y `payment-authorization` (U4) publican eventos de dominio vía Outbox Pattern hacia **Amazon EventBridge** [ADR-02].
  - `notification-webhooks` (U5) consume eventos desde SQS suscrito a reglas de EventBridge para despachar webhooks a canales externos.
  - Eventos principales: `order.confirmed`, `order.cancelled`, `payment.approved`, `payment.rejected`.

[Answer]: Síncrono interno vía REST HTTP privado con timeouts estrictos para reserva y pago; Asíncrono vía Amazon EventBridge + SQS con Outbox Pattern para eventos de dominio y notificaciones.

---

## CD-Q3. Propiedad de Contratos y Versionamiento

¿Quién es el dueño de cada especificación y cuál es la política de cambios y compatibilidad?

- **Propiedad:** La unidad proveedora (Provider Unit) es la dueña única de su especificación OpenAPI / AsyncAPI. El consumidor genera o implementa clientes basados en dicho contrato.
- **Versionamiento de APIs REST:** Versionamiento en URL mayor (`/v1/`). Cambios no destructivos (nuevos campos opcionales) no incrementan la versión mayor.
- **Regla de Robustez (Postel):** Los consumidores deben ignorar propiedades desconocidas en los payloads JSON para permitir evolución aditiva sin romper clientes.
- **Versionamiento de Eventos:** Eventos con versionamiento semántico en metadatos (`eventVersion: "1.0"`). Nuevos atributos son aditivos y opcionales.

[Answer]: La unidad proveedora es dueña del contrato. Versionamiento mayor en ruta `/v1/` y aditivo tolerante (ignorar campos desconocidos).

---

## CD-Q4. Manejo de Errores, Códigos HTTP y Reintentos

¿Cuál es el contrato estandarizado de errores en las APIs de la PIE?

- **Estructura de Error RFC 7807 (Problem Details for HTTP APIs):**
  Todos los endpoints retornan respuestas de error uniformes con Content-Type `application/problem+json`:
  - `type`: URI de tipo de error o código canónico (e.g., `urn:andes:error:stock-unavailable`).
  - `title`: Descripción corta y legible para humanos.
  - `status`: Código HTTP numérico (e.g., 400, 401, 403, 404, 422, 429, 500, 503).
  - `detail`: Detalle explicativo de la condición de error (libre de PII o datos sensibles [CA-01]).
  - `instance`: URI del recurso o llamada que causó el error.
  - `correlationId`: ID de correlación único para soporte técnico.
  - `timestamp`: Marca de tiempo ISO-8601 UTC.
- **Manejo de Reintentos:**
  - Código `429 Too Many Requests`: Incluye header `Retry-After: <segundos>`.
  - Código `503 Service Unavailable`: Incluye header `Retry-After: <segundos>`.
  - Errores 4xx (excepto 429) no deben reintentarse automáticamente sin corregir el request.

[Answer]: Estándar RFC 7807 (application/problem+json) en todas las APIs externas e internas con correlationId y Retry-After en 429/503.

---

## Assumptions & Open Questions

- [assumption] Todas las APIs externas e internas emplean esquemas OpenAPI 3.0.3 para REST y AsyncAPI 2.6 para eventos en EventBridge.
- [assumption] Las notificaciones de webhook hacia canales externos firman el payload en el encabezado `X-Hub-Signature-256` mediante HMAC-SHA256 utilizando la clave compartida de cada tenant.
