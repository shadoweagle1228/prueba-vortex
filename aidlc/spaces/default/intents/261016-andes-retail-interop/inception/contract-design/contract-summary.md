## Sources

- [desc] Initial description del proyecto Andes Retail.
- [scope] Scope document y backlog del intent.
- [FR-01] a [FR-12], [NFR-01] a [NFR-14] del requirements.md.
- [CA-01] Restricción absoluta PCI-DSS, segmentación CDE y enmascaramiento PII.
- [CA-02] Restricción absoluta backpressure en adaptadores legacy.
- [CA-03] Restricción absoluta Clean Architecture.
- [BC-ORDER], [BC-INVENTORY], [BC-PAYMENT], [BC-NOTIFICATION] del domain-design components.md.
- [ADR-01] a [ADR-06] del domain-design decisions.md.
- [U1] a [U8] del units-generation units.md.
- [CD-Q1] a [CD-Q4] del contract-design-questions.md.

---

# Resumen de Contratos — Andes Retail Interoperability Platform (PIE)

Este documento define formalmente todos los contratos de integración (interfaces públicas externas y límites inter-unidad) para la Plataforma de Integración y Eventos (PIE) de Andes Retail. Garantiza que los equipos puedan desarrollar los servicios en paralelo con acoplamiento mínimo y máxima confiabilidad.

---

## Tabla de Límites y Contratos

| # | Contrato | Unidad Proveedora | Consumidor | Mecanismo | Dueño |
|---|---|---|---|---|---|
| **C-01** | Inventory Stock API (`/v1/inventory/stock/{sku}`) | `skeleton-stock-query` (U1) | External: Canales Digitales | REST / HTTPS (OpenAPI 3.0) | `skeleton-stock-query` |
| **C-02** | Catalog Price API (`/v1/catalog/price/{sku}`) | `price-query` (U2) | External: Canales Digitales | REST / HTTPS (OpenAPI 3.0) | `price-query` |
| **C-03** | Orders API (`/v1/orders`, `/v1/orders/{id}`) | `order-lifecycle` (U3) | External: Canales Digitales | REST / HTTPS (OpenAPI 3.0) | `order-lifecycle` |
| **C-04** | Auth Token API (`/auth/token`) | `security-compliance` (U6) | External: Canales Digitales | REST / HTTPS (OpenAPI 3.0) | `security-compliance` |
| **C-05** | Stock Reservation Port (Internal) | `skeleton-stock-query` (U1) | `order-lifecycle` (U3) | REST / HTTP Pod-to-Pod (OpenAPI 3.0) | `skeleton-stock-query` |
| **C-06** | Payment Auth Port (Internal CDE) | `payment-authorization` (U4) | `order-lifecycle` (U3) | REST / HTTPS CDE Aislado (OpenAPI 3.0) | `payment-authorization` |
| **C-07** | Domain Events Bus (EventBridge) | `order-lifecycle` / `payment-auth` | `notification-webhooks` (U5), WMS/OMS | EventBridge Event Bus (AsyncAPI 2.6) | Arquitectura PIE |
| **C-08** | Outgoing Webhooks Delivery | `notification-webhooks` (U5) | External: Endpoints Canales | Webhook HTTP POST + HMAC (AsyncAPI 2.6) | `notification-webhooks` |
| **C-09** | ERP Adapter ACL Boundary | Sistema Legacy ERP (SAP) | `skeleton-stock-query` (U1) | ACL Connector / Circuit Breaker (Schema) | `skeleton-stock-query` |

---

## Especificaciones de Contratos

---

### C-01: Inventory Stock API (Pública)

API externa para consulta de existencias en tiempo real con soporte de caché Redis y resiliencia ante caídas del ERP [FR-01, NFR-01, CA-02].

```yaml
openapi: 3.0.3
info:
  title: Andes Retail - Inventory Stock API
  version: 1.0.0
  description: Consulta de stock disponible por SKU y bodega con alta velocidad y resiliencia.
paths:
  /v1/inventory/stock/{sku}:
    get:
      summary: Consultar stock disponible de un SKU
      operationId: getStockBySku
      security:
        - OAuth2Bearer: ["inventory:read"]
      parameters:
        - name: sku
          in: path
          required: true
          schema:
            type: string
            example: "SKU-COL-001"
        - name: warehouseId
          in: query
          required: false
          schema:
            type: string
            example: "WH-BOG-01"
        - name: channelId
          in: query
          required: false
          schema:
            type: string
            example: "ECOMMERCE"
        - name: X-Correlation-ID
          in: header
          required: false
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: Stock disponible retornado exitosamente
          headers:
            X-Stock-Source:
              schema:
                type: string
                enum: [cache, erp, stale-cache]
              description: Origen del dato reportado (stale-cache si CB está abierto)
            X-Correlation-ID:
              schema:
                type: string
                format: uuid
          content:
            application/json:
              schema:
                type: object
                required: [sku, available, reserved, lastUpdated, cacheHit]
                properties:
                  sku:
                    type: string
                    example: "SKU-COL-001"
                  warehouseId:
                    type: string
                    example: "WH-BOG-01"
                  available:
                    type: integer
                    minimum: 0
                    example: 145
                  reserved:
                    type: integer
                    minimum: 0
                    example: 10
                  lastUpdated:
                    type: string
                    format: date-time
                    example: "2026-10-16T22:30:00Z"
                  cacheHit:
                    type: boolean
                    example: true
        '401':
          $ref: '#/components/responses/Unauthorized'
        '403':
          $ref: '#/components/responses/Forbidden'
        '404':
          $ref: '#/components/responses/NotFound'
        '429':
          $ref: '#/components/responses/RateLimited'
        '503':
          $ref: '#/components/responses/ServiceUnavailable'
components:
  securitySchemes:
    OAuth2Bearer:
      type: http
      scheme: bearer
      bearerFormat: JWT
  responses:
    Unauthorized:
      description: Token inválido o ausente
      content:
        application/problem+json:
          schema:
            $ref: '#/components/schemas/ProblemDetails'
    Forbidden:
      description: Privilegios o scopes insuficientes
      content:
        application/problem+json:
          schema:
            $ref: '#/components/schemas/ProblemDetails'
    NotFound:
      description: Recurso no encontrado
      content:
        application/problem+json:
          schema:
            $ref: '#/components/schemas/ProblemDetails'
    RateLimited:
      description: Límite de tasa superado por el canal
      headers:
        Retry-After:
          schema:
            type: integer
      content:
        application/problem+json:
          schema:
            $ref: '#/components/schemas/ProblemDetails'
    ServiceUnavailable:
      description: Servicio degradado o ERP/Caché no disponible
      headers:
        Retry-After:
          schema:
            type: integer
      content:
        application/problem+json:
          schema:
            $ref: '#/components/schemas/ProblemDetails'
  schemas:
    ProblemDetails:
      type: object
      required: [type, title, status, detail, correlationId, timestamp]
      properties:
        type:
          type: string
          example: "urn:andes:error:stock-unavailable"
        title:
          type: string
          example: "Stock no disponible"
        status:
          type: integer
          example: 404
        detail:
          type: string
          example: "El SKU especificado no existe o no tiene bodega asignada"
        instance:
          type: string
          example: "/v1/inventory/stock/SKU-COL-001"
        correlationId:
          type: string
          format: uuid
          example: "a8098c1a-f86e-11da-bd1a-00112444be1e"
        timestamp:
          type: string
          format: date-time
          example: "2026-10-16T22:30:00Z"
```

---

### C-02: Catalog Price API (Pública)

API externa para consulta de precios vigentes y promocionales por canal y segmento de cliente [FR-02, NFR-01].

```yaml
openapi: 3.0.3
info:
  title: Andes Retail - Catalog Price API
  version: 1.0.0
  description: Consulta de precios vigentes, segmentados y promocionales.
paths:
  /v1/catalog/price/{sku}:
    get:
      summary: Consultar precio de un SKU
      operationId: getPriceBySku
      security:
        - OAuth2Bearer: ["catalog:read"]
      parameters:
        - name: sku
          in: path
          required: true
          schema:
            type: string
            example: "SKU-COL-001"
        - name: channelId
          in: query
          required: false
          schema:
            type: string
            example: "ECOMMERCE"
        - name: customerSegment
          in: query
          required: false
          schema:
            type: string
            enum: [REGULAR, VIP, CORPORATE, WHOLESALE]
            default: REGULAR
        - name: X-Correlation-ID
          in: header
          required: false
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: Precio retornado exitosamente
          content:
            application/json:
              schema:
                type: object
                required: [sku, currency, amountInCents, formattedAmount]
                properties:
                  sku:
                    type: string
                    example: "SKU-COL-001"
                  currency:
                    type: string
                    example: "COP"
                  amountInCents:
                    type: integer
                    format: int64
                    description: Monto exacto en centavos COP [ADR-01]
                    example: 9990000
                  formattedAmount:
                    type: string
                    example: "$99.900,00"
                  promotionalAmountInCents:
                    type: integer
                    format: int64
                    nullable: true
                    example: 7990000
                  promotionExpiry:
                    type: string
                    format: date-time
                    nullable: true
                    example: "2026-10-31T23:59:59Z"
                  priceListId:
                    type: string
                    example: "PL-ECOMM-REG-2026"
```

---

### C-03: Orders API (Pública)

API externa para creación y consulta de órdenes de compra con idempotencia dual estricta y protección PCI-DSS [FR-03, FR-04, FR-05, CA-01, ADR-01, ADR-05, ADR-06].

```yaml
openapi: 3.0.3
info:
  title: Andes Retail - Orders API
  version: 1.0.0
  description: Creación y consulta de órdenes con garantía de idempotencia y seguridad PCI-DSS.
paths:
  /v1/orders:
    post:
      summary: Crear una nueva orden de compra
      operationId: createOrder
      security:
        - OAuth2Bearer: ["orders:write"]
      parameters:
        - name: Idempotency-Key
          in: header
          required: true
          description: Identificador UUID único para evitar duplicación [FR-04, ADR-05]
          schema:
            type: string
            format: uuid
            example: "b3f0e0d2-9642-4f3b-8519-798df6714402"
        - name: X-Correlation-ID
          in: header
          required: false
          schema:
            type: string
            format: uuid
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [channelId, customerId, items, shippingAddress, paymentToken]
              properties:
                channelId:
                  type: string
                  example: "ECOMMERCE"
                customerId:
                  type: string
                  example: "CUST-98213"
                items:
                  type: array
                  minItems: 1
                  items:
                    type: object
                    required: [sku, quantity, unitPriceInCents]
                    properties:
                      sku:
                        type: string
                        example: "SKU-COL-001"
                      quantity:
                        type: integer
                        minimum: 1
                        example: 2
                      unitPriceInCents:
                        type: integer
                        format: int64
                        example: 9990000
                shippingAddress:
                  type: object
                  description: Dirección física del destinatario (enmascarada en logs [CA-01])
                  required: [street, city, department, postalCode, country]
                  properties:
                    street:
                      type: string
                      example: "Calle 100 # 15-20"
                    city:
                      type: string
                      example: "Bogota"
                    department:
                      type: string
                      example: "Cundinamarca"
                    postalCode:
                      type: string
                      example: "110111"
                    country:
                      type: string
                      default: "CO"
                      example: "CO"
                paymentToken:
                  type: string
                  description: Token seguro emitido por la pasarela PSP (NUNCA PAN [CA-01, ADR-06])
                  example: "tok_psp_and_9a87f61c2d3e"
      responses:
        '201':
          description: Orden creada y confirmada exitosamente
          headers:
            Idempotency-Replay:
              schema:
                type: boolean
              description: Verdadero si la respuesta es una reproducción por Idempotency-Key
            X-Correlation-ID:
              schema:
                type: string
                format: uuid
          content:
            application/json:
              schema:
                type: object
                required: [orderId, status, totalAmountInCents, createdAt]
                properties:
                  orderId:
                    type: string
                    format: uuid
                    example: "e6f47702-861c-4b53-832b-232145e41299"
                  status:
                    type: string
                    enum: [CONFIRMED, IN_PROGRESS]
                    example: "CONFIRMED"
                  totalAmountInCents:
                    type: integer
                    format: int64
                    example: 19980000
                  currency:
                    type: string
                    example: "COP"
                  estimatedDelivery:
                    type: string
                    format: date-time
                    example: "2026-10-20T18:00:00Z"
                  createdAt:
                    type: string
                    format: date-time
                    example: "2026-10-16T22:31:00Z"
        '422':
          description: Error semántico en el procesamiento (Stock insuficiente o pago rechazado)
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetails'
  /v1/orders/{orderId}:
    get:
      summary: Consultar estado de una orden
      operationId: getOrderById
      security:
        - OAuth2Bearer: ["orders:read", "orders:write"]
      parameters:
        - name: orderId
          in: path
          required: true
          schema:
            type: string
            format: uuid
        - name: X-Correlation-ID
          in: header
          required: false
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: Detalle y estado de la orden
          content:
            application/json:
              schema:
                type: object
                required: [orderId, tenantId, status, items, totalAmountInCents, createdAt]
                properties:
                  orderId:
                    type: string
                    format: uuid
                  tenantId:
                    type: string
                    example: "ECOMMERCE"
                  status:
                    type: string
                    enum: [CREATED, CONFIRMED, IN_PROGRESS, SHIPPED, DELIVERED, CANCELLED, PAYMENT_FAILED]
                    example: "CONFIRMED"
                  items:
                    type: array
                    items:
                      type: object
                      properties:
                        sku:
                          type: string
                        quantity:
                          type: integer
                        unitPriceInCents:
                          type: integer
                  totalAmountInCents:
                    type: integer
                  createdAt:
                    type: string
                    format: date-time
```

---

### C-04: Auth Token API (Keycloak IdP)

Especificación OAuth2 para emisión de JWTs multi-tenant mediante `client_credentials` [FR-11, NFR-07, CA-01].

```yaml
openapi: 3.0.3
info:
  title: Andes Retail - Identity & Access API (Keycloak OIDC)
  version: 1.0.0
paths:
  /auth/token:
    post:
      summary: Obtener token Bearer JWT mediante credenciales de canal
      operationId: getAuthToken
      requestBody:
        required: true
        content:
          application/x-www-form-urlencoded:
            schema:
              type: object
              required: [grant_type, client_id, client_secret]
              properties:
                grant_type:
                  type: string
                  enum: [client_credentials]
                  example: "client_credentials"
                client_id:
                  type: string
                  example: "marketplace-falabella"
                client_secret:
                  type: string
                  format: password
                  example: "sec_a918237198273"
                scope:
                  type: string
                  example: "inventory:read orders:write"
      responses:
        '200':
          description: Token JWT generado
          content:
            application/json:
              schema:
                type: object
                required: [access_token, token_type, expires_in]
                properties:
                  access_token:
                    type: string
                    description: JWT firmado con claims tenantId, scopes, roles
                  token_type:
                    type: string
                    example: "Bearer"
                  expires_in:
                    type: integer
                    example: 300
```

---

### C-05: Stock Reservation Port (Internal Inter-Unit Boundary)

Contrato privado síncrono entre `order-lifecycle` (U3) y `skeleton-stock-query` (U1) para reservar inventario durante la Saga y liberar en caso de compensación [FR-06, CA-02].

```yaml
openapi: 3.0.3
info:
  title: Internal Stock Reservation Port
  version: 1.0.0
  description: API interna consumida por Order Service para reservas atómicas de inventario.
paths:
  /internal/v1/stock/reservations:
    post:
      summary: Reservar stock para una orden en curso
      operationId: createStockReservation
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [reservationId, items]
              properties:
                reservationId:
                  type: string
                  format: uuid
                  description: Mismo Order ID para correlación directa
                items:
                  type: array
                  items:
                    type: object
                    required: [sku, quantity]
                    properties:
                      sku:
                        type: string
                      quantity:
                        type: integer
      responses:
        '200':
          description: Reserva efectuada con éxito
          content:
            application/json:
              schema:
                type: object
                required: [reservationId, status, reservedAt]
                properties:
                  reservationId:
                    type: string
                    format: uuid
                  status:
                    type: string
                    enum: [RESERVED]
                  reservedAt:
                    type: string
                    format: date-time
        '422':
          description: Stock insuficiente para uno o más SKUs
          content:
            application/problem+json:
              schema:
                type: object
                properties:
                  type:
                    type: string
                    example: "urn:andes:stock:insufficient"
                  unavailableSkus:
                    type: array
                    items:
                      type: string
  /internal/v1/stock/reservations/{reservationId}:
    delete:
      summary: Compensación Saga - Liberar stock reservado ante fallo de pago
      operationId: releaseStockReservation
      parameters:
        - name: reservationId
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '204':
          description: Stock liberado exitosamente en ERP y Caché Redis
```

---

### C-06: Payment Auth Port (Internal CDE Inter-Unit Boundary)

Contrato privado síncrono entre `order-lifecycle` (U3) y `payment-authorization` (U4) aislado en subred CDE privada [CA-01, ADR-06].

```yaml
openapi: 3.0.3
info:
  title: Internal Payment Authorization Port (CDE Isolated)
  version: 1.0.0
  description: Puerto de autorización con token hacia la pasarela PSP. Cumple aislamiento PCI-DSS.
paths:
  /internal/v1/payments/authorizations:
    post:
      summary: Solicitar autorización de cobro
      operationId: authorizePayment
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required: [orderId, paymentToken, amountInCents, currency]
              properties:
                orderId:
                  type: string
                  format: uuid
                paymentToken:
                  type: string
                  description: Token del PSP (redactado en logs del servicio [ADR-06])
                amountInCents:
                  type: integer
                  format: int64
                currency:
                  type: string
                  example: "COP"
      responses:
        '200':
          description: Resultado de autorización obtenido del PSP
          content:
            application/json:
              schema:
                type: object
                required: [authorized, orderId]
                properties:
                  authorized:
                    type: boolean
                  chargeId:
                    type: string
                    nullable: true
                    example: "ch_psp_881923"
                  declineCode:
                    type: string
                    nullable: true
                    example: "insufficient_funds"
                  last4:
                    type: string
                    nullable: true
                    example: "4242"
```

---

### C-07: Domain Events Bus (EventBridge AsyncAPI 2.6)

Especificación de eventos de dominio emitidos vía Outbox Pattern y orquestados en Amazon EventBridge [FR-07, FR-08, ADR-02].

```yaml
asyncapi: 2.6.0
info:
  title: Andes Retail - EventBridge Domain Events
  version: 1.0.0
  description: Bus de eventos de dominio sobre Amazon EventBridge para la PIE.
channels:
  andes.orders:
    publish:
      summary: Eventos del ciclo de vida de órdenes
      message:
        oneOf:
          - $ref: '#/components/messages/OrderConfirmedEvent'
          - $ref: '#/components/messages/OrderCancelledEvent'
  andes.payments:
    publish:
      summary: Eventos de resultados de pagos
      message:
        oneOf:
          - $ref: '#/components/messages/PaymentApprovedEvent'
          - $ref: '#/components/messages/PaymentRejectedEvent'
components:
  messages:
    OrderConfirmedEvent:
      name: OrderConfirmed
      title: Orden Confirmada
      payload:
        type: object
        required: [version, id, "detail-type", source, time, detail]
        properties:
          version:
            type: string
            example: "0"
          id:
            type: string
            format: uuid
          "detail-type":
            type: string
            example: "order.confirmed"
          source:
            type: string
            example: "andes.pie.order-service"
          time:
            type: string
            format: date-time
          detail:
            type: object
            required: [orderId, tenantId, totalAmountInCents, items, chargeId]
            properties:
              orderId:
                type: string
                format: uuid
              tenantId:
                type: string
                example: "ECOMMERCE"
              totalAmountInCents:
                type: integer
                format: int64
              chargeId:
                type: string
              items:
                type: array
                items:
                  type: object
                  properties:
                    sku:
                      type: string
                    quantity:
                      type: integer
    OrderCancelledEvent:
      name: OrderCancelled
      payload:
        type: object
        properties:
          "detail-type":
            type: string
            example: "order.cancelled"
          detail:
            type: object
            properties:
              orderId:
                type: string
              reason:
                type: string
    PaymentApprovedEvent:
      name: PaymentApproved
      payload:
        type: object
        properties:
          "detail-type":
            type: string
            example: "payment.approved"
          detail:
            type: object
            properties:
              orderId:
                type: string
              chargeId:
                type: string
    PaymentRejectedEvent:
      name: PaymentRejected
      payload:
        type: object
        properties:
          "detail-type":
            type: string
            example: "payment.rejected"
          detail:
            type: object
            properties:
              orderId:
                type: string
              declineCode:
                type: string
```

---

### C-08: Outgoing Webhooks Delivery (AsyncAPI 2.6)

Contrato de entrega de notificaciones salientes a los canales externos registrados, firmado con HMAC-SHA256 [FR-07, FR-08, FR-10, CA-01].

```yaml
asyncapi: 2.6.0
info:
  title: Andes Retail - Outgoing Webhooks Specification
  version: 1.0.0
  description: Webhooks HTTP POST despachados por Notification Service hacia endpoints HTTPS de los canales.
channels:
  channelWebhookEndpoint:
    subscribe:
      summary: Despacho HTTP POST hacia el canal
      bindings:
        http:
          type: request
          method: POST
      message:
        headers:
          type: object
          required: [Content-Type, X-Hub-Signature-256, X-Correlation-ID, X-Event-Type]
          properties:
            Content-Type:
              type: string
              example: "application/json"
            X-Hub-Signature-256:
              type: string
              description: Firma HMAC-SHA256 del cuerpo del payload con el secreto del canal
              example: "sha256=9b71d224bd62f3785d96d46ad3ea3d7331ecabb020937fbc383acf50f02bd71e"
            X-Correlation-ID:
              type: string
              format: uuid
            X-Event-Type:
              type: string
              enum: [order.confirmed, payment.approved, payment.rejected]
        payload:
          type: object
          required: [eventId, eventType, timestamp, tenantId, data]
          properties:
            eventId:
              type: string
              format: uuid
            eventType:
              type: string
              example: "order.confirmed"
            timestamp:
              type: string
              format: date-time
            tenantId:
              type: string
              example: "MARKETPLACE_A"
            data:
              type: object
              description: Payload de la orden o pago (estrictamente sin PII [CA-01])
              properties:
                orderId:
                  type: string
                  format: uuid
                status:
                  type: string
                chargeId:
                  type: string
```

---

### C-09: ERP Adapter ACL Boundary (Shared Schema)

Contrato entre el Stock Adapter (ACL) y el sistema ERP legacy on-premise, resguardado por Circuit Breaker (Resilience4j) y Bulkhead [CA-02, ADR-03, ADR-04].

```yaml
shared-schema:
  title: Legacy ERP ACL Connector Specification
  version: 1.0.0
  boundary_type: Anti-Corruption Layer (ACL)
  downstream_legacy: SAP ECC / S4HANA on-premise
  resilience_policy:
    circuit_breaker:
      failure_rate_threshold: 50%
      sliding_window_size: 30
      wait_duration_in_open_state: 60s
      permitted_calls_in_half_open: 5
    bulkhead:
      max_concurrent_calls: 20
      max_wait_duration: 100ms
    timeout:
      connect_timeout: 100ms
      read_timeout: 250ms
    fallback_strategy: "Fail-closed with Redis Stale Cache"
  data_mappings:
    erp_to_domain:
      MATNR: "sku"
      WERKS: "warehouseId"
      LABST: "available"
      UMLMC: "reserved"
    domain_to_erp_reservation:
      sku: "MATNR"
      warehouseId: "WERKS"
      quantity: "MENGE"
      orderId: "EBELN"
```

---

## Contract Ownership Rules

1. **Propiedad por Unidad:** Cada unidad proveedora (Provider Unit) es la única responsable y dueña del mantenimiento y evolución de su contrato OpenAPI/AsyncAPI.
2. **Evolución No Destructiva (Compatibilidad Hacia Adelante):** Cualquier cambio de contrato debe ser estrictamente aditivo (nuevos campos opcionales). La eliminación de campos o cambios de tipo requieren un incremento en la versión mayor de la URL (`/v2/`).
3. **Regla de Robustez (Postel):** Todos los consumidores (internos y externos) deben ignorar propiedades desconocidas en los payloads JSON para evitar rupturas por adiciones en el contrato.
4. **Gobierno de Eventos:** Los esquemas de eventos en EventBridge son registrados en el AWS Schema Registry para validación estricta en runtime.
5. **Aislamiento CDE:** El contrato de pagos (C-06) solo puede ser consumido por pods autorizados dentro del CDE o mediante IAM Roles específicos, asegurando que ningún payload con tokens o datos financieros viaje por la red abierta de Kubernetes.

---

## Open Questions

| Contrato | Pregunta | Bloquea |
|---|---|---|
| C-08 (Webhooks) | ¿El canal piloto registrará su endpoint mediante API de autogestión o en el proceso de onboarding en Keycloak/Kong? | Despliegue de UI de autogestión (En MVP se registra en configuración durante onboarding) |
| C-09 (ERP ACL) | ¿El ERP provee endpoints REST nativos o se requiere conector RFC/BAPI mediante puente SAP? | Implementación detallada del conector de bajo nivel en Construction |

---

## Assumptions & Open Questions

- [assumption] Todos los canales externos consumen las APIs a través del API Gateway / Kong Ingress Controller con certificados TLS 1.3 gestionados por AWS Certificate Manager.
- [assumption] Las cuotas y rate limits por defecto para los canales se fijan en 100 req/s con burst de 150 req/s, configurable por `tenantId` en Kong.
- [assumption] Para el MVP, el catálogo y precios comparten el microservicio de inventario (`stock-price-adapter`), manteniendo esquemas OpenAPI separados para permitir desacoplamiento futuro sin cambiar las URLs públicas.
