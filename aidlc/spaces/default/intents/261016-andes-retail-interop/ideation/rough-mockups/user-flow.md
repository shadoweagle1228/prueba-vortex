## Sources

- [desc] Requerimientos funcionales: APIs externas y flujos principales.
- [scope] Intent backlog — 5 bloques secuenciales.
- [CA-01] PCI-DSS — flujo de pago con token únicamente.
- [CA-02] Backpressure — Circuit Breaker en flujos hacia legados.
- [Q8] Multi-tenant por canal.

---

## User Flow — Interacciones de Sistema

### Flujo A: Consulta de Stock/Precio (Bloque SP del backlog)

```
ACTOR: Canal externo (marketplace o e-commerce)
ENTRADA: Token JWT válido + SKU + warehouseId (opcional)
SALIDA: Stock disponible con latencia p95 < 300ms

PASOS:
1. Canal → [JWT válido con scope inventory:read] → Stock Adapter
2. Stock Adapter → [¿Cache hit en Redis?]
   SI → retornar en < 50ms                          (90% casos)
   NO → [¿Circuit Breaker ERP abierto?]
      SI → retornar stale cache + header indicador  (< 1% casos)
      NO → llamar ERP (timeout 250ms)
         OK  → actualizar cache + retornar          (9% casos)
         FAIL → retornar stale cache                (< 1% casos)

VARIANTE ERROR:
- Sin JWT → 401 Unauthorized
- JWT con scope incorrecto → 403 Forbidden
- ERP offline + cache expirado → 503 Service Unavailable + Retry-After: 5
```

---

### Flujo B: Crear Orden (Bloque O del backlog) — Flujo principal

```
ACTOR: Canal externo (e-commerce o marketplace)
ENTRADA: Token JWT + items + dirección de envío + payment token
SALIDA: orderId confirmado o error con razón específica

PASOS (happy path):
1. Canal → [JWT válido + scope orders:write + Idempotency-Key] → Order Service
2. Order Service → [¿Idempotency-Key ya procesado?]
   SI → retornar respuesta original (idempotent replay)
3. Order Service → Stock Adapter → [Reservar stock en ERP]
   FAIL (stock insuficiente) → 422 stock-unavailable
4. Order Service → Payment Service → [Autorizar pago con token]
   FAIL (pago rechazado) → [COMPENSAR: liberar reserva] → 422 payment-declined
5. Order Service → [Persistir orden + outbox event en Aurora (1 TX atómica)]
6. Order Service → [Guardar idempotency key en Redis TTL=24h]
7. Order Service → 201 Created {orderId, status: CONFIRMED}
8. [ASYNC] EventBridge → WMS Adapter → ERP (fulfillment)
9. [ASYNC] EventBridge → Notification Service → Canal (webhook + email/SMS)
```

---

### Flujo C: Webhooks Salientes (Bloque W del backlog)

```
ACTOR: Notification Service (interno) → Canal externo (receptor)
TRIGGER: Evento en EventBridge (order.confirmed, payment.approved, etc.)

PASOS:
1. EventBridge → SQS notification-queue → Notification Service
2. Notification Service → [POST webhook al endpoint registrado del canal]
   OK (2xx) → mensaje procesado, eliminar de SQS
   FAIL (no-2xx o timeout) → [Reintento 1: +30s]
                            → [Reintento 2: +60s]
                            → [Reintento 3: +120s]
                            → [FAIL: mover a DLQ]
                            → [CloudWatch Alarm → SNS → alerta operacional]
```

---

### Flujo D: Onboarding de Canal Nuevo (flujo operacional)

```
ACTOR: Equipo de Vortexbird/AndesRetail + representante del canal externo
DURACIÓN ESTIMADA: 2-4 horas de configuración

PASOS:
1. Crear client en Keycloak con:
   - clientId único por canal
   - clientSecret rotado via Secrets Manager
   - scopes asignados según tipo de canal
   - rate limits configurados en Kong (requests/s y requests/min)
2. Registrar endpoint de webhook del canal (URL + secret de firma HMAC)
3. Entregar credenciales al canal via canal seguro (nunca email plano)
4. Canal prueba autenticación con /auth/token
5. Canal prueba API de stock y precio en entorno staging
6. Activar en producción
```

---

### Matriz de Flujos vs. Restricciones Absolutas

| Flujo | CA-01 PCI/PII | CA-02 Backpressure | CA-03 Clean Arch |
|-------|--------------|-------------------|-----------------|
| A — Stock/Precio | PII enmascarado en logs; sin datos de pago | CB + Cache Redis como barrera | Stock Adapter: domain/application/infra |
| B — Crear Orden | Token de pago (no PAN); PII enmascarado | CB en Stock Adapter; timeout PSP fail-closed | Order Service: domain/application/infra |
| C — Webhooks | Sin datos sensibles en payload saliente | DLQ + reintentos con backoff | Notification Service desacoplado |
| D — Onboarding | Credenciales via Secrets Manager | Rate limit por canal desde el inicio | Configuración IaC (no manual) |

---

## Assumptions & Open Questions

- [assumption] El Flujo D (onboarding de canal) asume que el endpoint de webhook del canal externo es HTTPS con TLS válido. Canales con endpoints HTTP o certificados self-signed serán rechazados.
- [assumption] Los tiempos de reintento de webhooks (30s, 60s, 120s) son el default. Cada canal puede tener una política diferente configurada en SQS si lo requiere.
