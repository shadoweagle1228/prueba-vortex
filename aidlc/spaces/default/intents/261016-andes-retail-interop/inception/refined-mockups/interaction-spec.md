## Sources

- [FR-01] a [FR-12] del requirements.md.
- [CA-01] PCI-DSS.
- [CA-02] Backpressure.
- [TF-stack] Kotlin + Spring Boot.

---

## Interaction Spec — Contratos de API (Pre-OpenAPI)

### API 1: GET /v1/inventory/stock/{sku}

```
Method:  GET
Path:    /v1/inventory/stock/{sku}
Auth:    Bearer JWT (scope: inventory:read)
Headers: X-Correlation-ID: <UUID>  (obligatorio)
         X-Tenant-ID: <string>      (extraído del JWT por Kong)

Query params:
  warehouseId  string  optional  ID de bodega (si omitido, retorna todas)
  channelId    string  optional  ID de canal para reglas de visibilidad

Response 200 OK:
{
  "sku": "string",
  "warehouseId": "string",
  "available": integer,
  "reserved": integer,
  "lastUpdated": "ISO-8601",
  "cacheHit": boolean,
  "source": "CACHE | ERP | STALE_CACHE"
}

Response 403: { "type": "forbidden", "status": 403, "correlationId": "..." }
Response 503: { "type": "service-unavailable", "status": 503, "retryAfter": 5 }

SLA: p95 ≤ 300ms | Cache hit p95 ≤ 50ms
PII: ningún campo de respuesta contiene datos personales [CA-01]
```

### API 2: GET /v1/catalog/price/{sku}

```
Method:  GET
Path:    /v1/catalog/price/{sku}
Auth:    Bearer JWT (scope: catalog:read)

Query params:
  channelId        string  required
  customerSegment  string  optional  (REGULAR | PREMIUM | WHOLESALE)

Response 200 OK:
{
  "sku": "string",
  "channelId": "string",
  "price": integer,         (en centavos COP para evitar decimales flotantes)
  "currency": "COP",
  "promotionalPrice": integer | null,
  "promotionExpiry": "ISO-8601" | null,
  "priceListId": "string"
}
```

### API 3: POST /v1/orders

```
Method:   POST
Path:     /v1/orders
Auth:     Bearer JWT (scope: orders:write)
Headers:  Idempotency-Key: <UUID v4>  (obligatorio)
          X-Correlation-ID: <UUID>

Request body:
{
  "channelId": "string",
  "customerId": "string",        (ID del cliente — nunca PAN ni datos de tarjeta)
  "items": [
    {
      "sku": "string",
      "quantity": integer,
      "unitPrice": integer       (precio confirmado en la consulta previa)
    }
  ],
  "shippingAddress": {
    "street": "string",
    "city": "string",
    "department": "string",      (Colombia: departamento)
    "postalCode": "string",
    "country": "CO"
  },
  "paymentToken": "string"       (token PSP — NUNCA el PAN) [CA-01]
}

Response 201 Created:
{
  "orderId": "string",
  "status": "CONFIRMED",
  "estimatedDelivery": "ISO-8601 date",
  "totalAmount": integer,
  "currency": "COP",
  "createdAt": "ISO-8601",
  "_links": {
    "self": "/v1/orders/{orderId}",
    "track": "/v1/orders/{orderId}/tracking"
  }
}

Response 409: Idempotency-Replay: true  → retorna la respuesta original
Response 422 stock-unavailable: { "type": "stock-unavailable", "status": 422, "detail": "..." }
Response 422 payment-declined: { "type": "payment-declined", "status": 422, "declineCode": "..." }

LOG POLICY [CA-01]:
- paymentToken: registrar solo el prefijo (tok_visa_***REDACTED***)
- customerId: registrar (no es PII directa en el log de aplicación)
- shippingAddress: NUNCA registrar en logs de aplicación
- items: registrar SKU y cantidad; NO registrar precios unitarios en debug logs
```

### API 4: GET /v1/orders/{orderId}

```
Method:  GET
Path:    /v1/orders/{orderId}
Auth:    Bearer JWT (scope: orders:read)
         Tenant check: orderId debe pertenecer al tenantId del JWT

Response 200 OK:
{
  "orderId": "string",
  "status": "CREATED | CONFIRMED | IN_PROGRESS | SHIPPED | DELIVERED | CANCELLED | PAYMENT_FAILED | REJECTED",
  "channelId": "string",
  "items": [ { "sku": "string", "quantity": integer } ],
  "estimatedDelivery": "ISO-8601 date" | null,
  "tracking": {
    "carrier": "string" | null,
    "trackingNumber": "string" | null,
    "lastEvent": "string" | null
  },
  "createdAt": "ISO-8601",
  "updatedAt": "ISO-8601"
}

Response 403: JWT válido pero orderId de otro tenant
Response 404: orderId no existe
```

### Modelo de Error Unificado (RFC 7807)

```json
{
  "type": "https://api.andesretail.com/errors/{error-slug}",
  "title": "string",
  "status": integer,
  "detail": "string",
  "instance": "/v1/{resource}",
  "correlationId": "string",
  "timestamp": "ISO-8601"
}
```

### Headers de Correlación (todos los endpoints)

```
Request headers propagados por Kong:
  X-Correlation-ID   UUID único por request (generado por el cliente o Kong si ausente)
  X-Request-ID       UUID único por hop (generado por Kong)
  X-Tenant-ID        Extraído del JWT claim tenantId
  X-Forwarded-For    IP del cliente (enmascarada en logs de aplicación) [CA-01]

Response headers:
  X-Correlation-ID   El mismo que llegó en el request
  X-Response-Time    Latencia en ms
  X-Stock-Source     Solo en GET /stock: CACHE | ERP | STALE_CACHE
```

---

## Assumptions & Open Questions

- [assumption] Los precios se manejan en centavos (integer) para evitar errores de punto flotante en la lógica de negocio. El frontend convierte a formato display (e.g. 99900 → $999.00 COP).
- [assumption] `shippingAddress.country` es siempre "CO" en el MVP (AndesRetail opera en Colombia). Se agrega validación explícita para rechazar países no soportados con 422.
