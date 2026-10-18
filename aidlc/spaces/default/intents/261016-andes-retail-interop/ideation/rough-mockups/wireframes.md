## Sources

- [desc] Initial description del proyecto.
- [CA-01] PCI-DSS — segmentación CDE y flujo de tokenización.
- [CA-02] Backpressure — Cache Redis como barrera ante legados.
- [CA-03] Clean Architecture — capas domain/application/infrastructure en cada servicio.
- [TF-stack] Kotlin + Spring Boot / Python CDK.
- [scope] Scope document — APIs v1 definidas.

---

## System Context Diagrams

### Diagrama 1: Contexto del Sistema — Plataforma de Integración y Eventos (PIE)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                         ACTORES EXTERNOS                                        │
│                                                                                 │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐    │
│  │  E-Commerce  │   │ Marketplace  │   │  App Móvil   │   │  Webhook     │    │
│  │  (Canal A)   │   │  (Canal B)   │   │  (Canal C)   │   │  Receiver    │    │
│  └──────┬───────┘   └──────┬───────┘   └──────┬───────┘   └──────▲───────┘    │
└─────────┼─────────────────┼─────────────────┼────────────────────┼────────────┘
          │ HTTPS/JWT        │ HTTPS/JWT        │ HTTPS/JWT          │ Webhooks
          ▼                  ▼                  ▼                    │
┌─────────────────────────────────────────────────────────────────────────────────┐
│  AWS EDGE  │  Route53 → WAF → AWS API Gateway (REST) → ALB                    │
└─────────────────────────────────────────────────────────────────────────────────┘
          │ HTTPS interno
          ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│  PLATAFORMA DE INTEGRACIÓN Y EVENTOS (PIE) — VPC Privada / EKS                │
│                                                                                 │
│  ┌─────────────┐    ┌──────────────────────────────────────────────────────┐   │
│  │   Keycloak  │    │  Kong Ingress Controller                             │   │
│  │  OIDC/OAuth │◄───│  JWT validate · Rate limit · CORS · Route            │   │
│  │  Multi-tenant│    └──────┬───────────────┬──────────────┬───────────────┘   │
│  └─────────────┘           │               │              │                   │
│                             ▼               ▼              ▼                   │
│  ┌──────────────┐   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │Order Service │   │Stock/Price   │  │Payment       │  │Notification  │     │
│  │Clean Arch    │   │Adapter (ACL) │  │Service       │  │Service       │     │
│  │Kotlin/SBoot  │   │CB+Bulkhead   │  │Token-only    │  │SES/SNS       │     │
│  └──────┬───────┘   └──────┬───────┘  └──────┬───────┘  └──────▲───────┘     │
│         │                  │                  │                  │             │
│         ▼                  ▼                  │                  │             │
│  ┌──────────────────────────────────────────┐ │  ┌──────────────────────────┐ │
│  │  Amazon EventBridge (order-events bus)   │─┘  │  SQS + DLQ               │ │
│  │  Schema Registry · Domain buses         │────►│  notification-queue      │─┘ │
│  └──────────────────────────────────────────┘    └──────────────────────────┘  │
│                                                                                 │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐                       │
│  │ RDS Aurora   │   │ElastiCache   │   │AWS Secrets   │  ← CDE (subnets       │
│  │ PostgreSQL   │   │Redis Cluster │   │Manager + KMS │    aisladas) [CA-01]  │
│  │ (CDE) [CA-01]│   │TLS+KMS       │   │VPC Endpoint  │                       │
│  └──────────────┘   └──────────────┘   └──────────────┘                       │
└─────────────────────────────────────────────────────────────────────────────────┘
          │ Direct Connect / VPN (< 250ms RTT)
          ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│  ON-PREMISE (AndesRetail Datacenter)                                           │
│                                                                                 │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   │
│  │  ERP (SAP)   │   │     WMS      │   │     OMS      │   │     CRM      │   │
│  │  Stock, Cat  │   │  Fulfillment │   │   Órdenes    │   │   Clientes   │   │
│  │  Precios     │   │  Warehouse   │   │   Legacy     │   │   Fideliz.   │   │
│  └──────────────┘   └──────────────┘   └──────────────┘   └──────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘

         PSP / Pasarela de Pagos (Externa) ←── Payment Service (token únicamente)
         [CA-01] El PAN nunca llega a la PIE — solo token de pago
```

---

### Diagrama 2: Flujo de Consulta Stock/Precio (happy path con cache)

```
Canal Externo          API Gateway           Kong              Stock Adapter         Redis         ERP
     │                     │                  │                     │                  │             │
     │─ GET /v1/inventory ─►│                  │                     │                  │             │
     │  /stock/{sku}        │─ JWT validate ──►│                     │                  │             │
     │  Bearer {JWT}        │                  │─ Rate limit check ──►                  │             │
     │                      │                  │  scope=inventory:read│                  │             │
     │                      │                  │─ Route to adapter ──►│                  │             │
     │                      │                  │                     │─ GET stock:{sku}─►│             │
     │                      │                  │                     │                  │             │
     │                      │   CACHE HIT (90% de los casos)        │◄─ {available:145}─│             │
     │◄─────────────────────────────────────── 200 OK p95 < 50ms ───│                  │             │
     │                      │                  │                     │                  │             │
     │                      │   CACHE MISS (10% de los casos)       │◄─ nil ────────────│             │
     │                      │                  │                     │─ GET /api/stock ──────────────►│
     │                      │                  │                     │  timeout: 250ms   │             │
     │                      │                  │                     │◄─ {available:145}─────────────│
     │                      │                  │                     │─ SET stock:{sku} ─►│             │
     │                      │                  │                     │  TTL=60s           │             │
     │◄─────────────────────────────────────── 200 OK p95 < 300ms ──│                  │             │
     │                      │                  │                     │                  │             │
     │  ERP TIMEOUT / CB OPEN (< 1% de los casos)                   │                  │             │
     │                      │                  │                     │─ GET stock stale ─►│             │
     │◄─ 200 OK ────────────────────────────────────────────────────│  X-Stock-Source:  │             │
     │   X-Stock-Source:    │                  │                     │  stale-cache      │             │
     │   stale-cache        │                  │                     │                  │             │
```

---

### Diagrama 3: Flujo Crear Orden — Saga con Compensación (vista de bloques)

```
                    ┌─────────────────────────────────────────────┐
                    │         FLUJO CREAR ORDEN (SAGA)            │
                    └─────────────────────────────────────────────┘

POST /v1/orders ──► [1. Validar JWT + scope orders:write]
                         │
                    [2. Idempotencia: ¿Idempotency-Key ya existe en Redis?]
                         │ NO existe
                    [3. SAGA STEP 1: Reservar stock en ERP via Stock Adapter]
                         │ Reserva OK              │ Stock insuficiente
                         │                         └──► 422 (sin compensación)
                    [4. SAGA STEP 2: Autorizar pago via Payment Service → PSP]
                         │ Pago OK                 │ Pago rechazado
                         │                         └──► [COMPENSAR: liberar reserva]
                         │                                   └──► 422 Payment Declined
                    [5. Persistir orden en Aurora + outbox event (misma TX)]
                    [6. Guardar idempotency key en Redis TTL=24h]
                    [7. Publicar OrderCreated en EventBridge]
                         │
                    [8. 201 Created {orderId, status: CONFIRMED}]
                         │
                    [ASYNC] EventBridge ──► SQS ──► WMS Adapter (fulfillment)
                    [ASYNC] EventBridge ──► SQS ──► Notification Service (email/SMS)

Notas de diseño [CA-01][CA-02][CA-03]:
- El Order Service (Clean Architecture) solo conoce el token de pago, nunca el PAN
- La reserva de stock protege el ERP con Circuit Breaker + timeout 250ms
- El outbox pattern garantiza que el evento se publica aunque el servicio reinicie
- El log de cada paso enmascara email, teléfono y dirección [CA-01]
```

---

### Diagrama 4: Flujo OAuth2/OIDC — Autenticación Multi-Tenant

```
Canal Externo (tenant: MARKETPLACE_A)     Keycloak (realm: retail)
        │                                        │
        │── POST /auth/token ───────────────────►│
        │   grant_type=client_credentials         │
        │   client_id=marketplace-a               │
        │   client_secret=***                     │
        │                                         │
        │                              [Verifica client_id activo]
        │                              [Verifica client_secret]
        │                              [Genera JWT con claims:]
        │                              {tenantId: MARKETPLACE_A,
        │                               scope: inventory:read orders:write,
        │                               exp: +5min}
        │◄── 200 OK {access_token: JWT} ─────────│
        │                                        │
        │── GET /v1/inventory/stock/SKU-001 ─────────────────────►  API GW
        │   Authorization: Bearer {JWT}                              │
        │                                                    [Valida firma JWT]
        │                                                    [Verifica exp]
        │                                                    [Verifica aud]
        │                                                            │
        │                                                    ──────► Kong
        │                                                    [Rate limit by tenantId]
        │                                                    [X-Tenant-ID: MARKETPLACE_A]
        │                                                            │
        │                                                    ──────► Stock Adapter
        │                                                    [Verifica scope inventory:read]
        │                                                    [Filtra por tenantId]

AISLAMIENTO MULTI-TENANT [CA-01]:
- MARKETPLACE_A no puede ver datos de MARKETPLACE_B
- Rate limit por tenantId es independiente
- Los scopes determinan qué APIs puede llamar cada canal
```

---

## Assumptions & Open Questions

- [assumption] Los diagramas ASCII son representaciones de diseño de nivel de ideación. Los diagramas Mermaid definitivos (.mmd) se producen en la fase de inception como parte de los entregables formales de arquitectura.
- [assumption] El flujo de compensación del Saga (Diagrama 3) asume que la reserva de stock en el ERP es una operación atómica reversible. Si el ERP no soporta reservas atómicas, la estrategia de compensación debe ajustarse en el diseño detallado de inception.
