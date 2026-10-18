## Sources

- [desc] Requerimientos del brief.
- [scope] Scope document — APIs v1.
- [FR-01] a [FR-12] del requirements.md.
- [CA-01] PCI-DSS y enmascaramiento PII.
- [CA-02] Backpressure en adaptadores legacy.
- [CA-03] Clean Architecture.
- [TF-stack] Kotlin + Spring Boot / Python CDK.

---

## Refined Interaction Diagrams — API Platform

Este stage refina los diagramas de sistema del Rough Mockups con los contratos
de request/response detallados surgidos de las User Stories y Requirements Analysis.

---

### Diagrama 1: Arquitectura de Capas — Clean Architecture por Servicio

```
┌──────────────────────────────────────────────────────────────────┐
│  Order Service — Estructura Clean Architecture [CA-03]           │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  DOMAIN LAYER (núcleo — cero dependencias externas)      │    │
│  │                                                          │    │
│  │  Entities: Order, OrderItem, OrderStatus, PaymentToken   │    │
│  │  Value Objects: Money, SKU, CustomerId, CorrelationId    │    │
│  │  Domain Events: OrderCreated, OrderConfirmed,            │    │
│  │                 OrderCancelled                           │    │
│  │  Repository Interfaces: OrderRepository                  │    │
│  │  Service Interfaces: StockService, PaymentService        │    │
│  └─────────────────────────────────────────────────────────┘    │
│                    ↑ depende de (hacia adentro)                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  APPLICATION LAYER (casos de uso)                        │    │
│  │                                                          │    │
│  │  Use Cases: CreateOrderUseCase, GetOrderStatusUseCase    │    │
│  │  Commands: CreateOrderCommand                            │    │
│  │  Queries: GetOrderQuery                                  │    │
│  │  Event Handlers: StockReservationFailedHandler           │    │
│  └─────────────────────────────────────────────────────────┘    │
│                    ↑ depende de (hacia adentro)                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  INFRASTRUCTURE LAYER (adaptadores externos)             │    │
│  │                                                          │    │
│  │  Web: OrderController (Spring MVC)                       │    │
│  │  DB: OrderRepositoryImpl (Spring Data JPA + Aurora)      │    │
│  │  Cache: IdempotencyStore (Spring Data Redis)             │    │
│  │  Events: EventBridgePublisher (AWS SDK)                  │    │
│  │  Adapters: StockServiceAdapter → Stock Adapter service   │    │
│  │            PaymentServiceAdapter → Payment Service       │    │
│  │  Security: JwtAuthFilter, LogSanitizingFilter [CA-01]    │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  ArchUnit test verifica: domain no importa infra/application     │
└──────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────┐
│  Stock/Price Adapter — Estructura Clean Architecture [CA-03]     │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  DOMAIN LAYER                                            │    │
│  │  Entities: StockLevel, Price, SKU, WarehouseId           │    │
│  │  Service Interfaces: ErpStockPort, ErpPricePort          │    │
│  └─────────────────────────────────────────────────────────┘    │
│                    ↑                                             │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  APPLICATION LAYER                                       │    │
│  │  Use Cases: QueryStockUseCase, QueryPriceUseCase         │    │
│  │             ReserveStockUseCase, ReleaseStockUseCase     │    │
│  └─────────────────────────────────────────────────────────┘    │
│                    ↑                                             │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  INFRASTRUCTURE LAYER (ACL hacia ERP) [CA-02]           │    │
│  │  Web: StockController, PriceController                   │    │
│  │  Cache: StockCacheAdapter (Redis) TTL=60s                │    │
│  │  ERP Adapter: ErpRestAdapter / ErpSoapAdapter            │    │
│  │    └── Circuit Breaker (Resilience4j) threshold=50%      │    │
│  │    └── Bulkhead (ThreadPool max=20, queue=50)            │    │
│  │    └── Timeout (250ms, fail-closed)                      │    │
│  │    └── Retry (2 reintentos con backoff exponencial)      │    │
│  └─────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────┘
```

---

### Diagrama 2: Flujo de Creación de Orden — Refinado con Detalles Técnicos

```
Cliente (P-01)    Kong     Order Service       Stock Adapter     Payment Svc    EventBridge
     │             │       AppLayer Domain      AppLayer         │               │
     │─POST /v1/orders──►  │              │              │       │               │
     │  JWT + IK   │       │              │              │       │               │
     │             │─JWT──►│              │              │       │               │
     │             │  OK   │              │              │       │               │
     │             │       │─IdempCheck──►│              │       │               │
     │             │       │ Redis GET    │              │       │               │
     │             │       │◄─nil         │              │       │               │
     │             │       │              │              │       │               │
     │             │       │──CreateOrderCommand──►      │       │               │
     │             │       │              │─ReserveStock─►       │               │
     │             │       │              │ ErpRestAdapter       │               │
     │             │       │              │  CB=CLOSED           │               │
     │             │       │              │◄─RESERVED─────       │               │
     │             │       │              │              │       │               │
     │             │       │              │──────────────►AuthorizePayment──►PSP │
     │             │       │              │              │  token (no PAN)       │
     │             │       │              │              │◄─AUTHORIZED───────    │
     │             │       │              │              │       │               │
     │             │       │─TX─────────────────────────────────────────────►   │
     │             │       │ INSERT order + outbox_event (1 transacción Aurora)  │
     │             │       │◄─COMMIT                    │       │               │
     │             │       │              │              │       │               │
     │             │       │─Redis SET IK (TTL=24h)     │       │               │
     │             │       │◄─OK                        │       │               │
     │◄─201 Created─────────              │              │       │               │
     │  {orderId,  │       │              │              │       │               │
     │   CONFIRMED}│       │              │              │       │               │
     │             │       │                                     │               │
     │             │       │─Outbox Poller──────────────────────────────────►   │
     │             │       │              PutEvents(OrderCreated)│               │
     │             │       │              bus=order-events       │               │
     │             │       │              schema validated       │               │
     │             │       │◄─OK                                │               │
     │             │       │                                     │               │
     │ [ASYNC]     │       │                                EventBridge──►SQS──►WMS
     │ [ASYNC]     │       │                                EventBridge──►SQS──►Notification
```

---

### Diagrama 3: Estado de una Orden — Máquina de Estados

```
                    ┌─────────────┐
                    │   CREATED   │  (orden registrada, pago pendiente)
                    └──────┬──────┘
                           │ pago autorizado
                    ┌──────▼──────┐
                    │  CONFIRMED  │  (pago ok, en cola para fulfillment)
                    └──────┬──────┘
                           │ WMS acepta la orden
                    ┌──────▼──────┐
                    │ IN_PROGRESS │  (fulfillment en curso)
                    └──────┬──────┘
                           │ despacho iniciado
                    ┌──────▼──────┐
                    │   SHIPPED   │  (en tránsito)
                    └──────┬──────┘
                           │ entrega confirmada
                    ┌──────▼──────┐
                    │  DELIVERED  │  (estado final exitoso)
                    └─────────────┘

Rutas de compensación / error:
  CREATED ──[stock insuficiente]──► REJECTED
  CREATED ──[pago rechazado]──────► [liberar reserva] ──► PAYMENT_FAILED
  IN_PROGRESS ──[cancelación]─────► [compensación WMS] ──► CANCELLED
  Cualquier estado ──[timeout]─────► evento en DLQ ──► revisión manual
```

---

### Diagrama 4: Topología de Red — CDE y Segmentación [CA-01]

```
┌─────────────────────────────────────────────────────────────────┐
│  INTERNET                                                       │
│  Canales externos (P-01) → HTTPS/TLS 1.3                       │
└─────────────────────┬───────────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────────┐
│  AWS EDGE (Public)                                              │
│  Route53 → WAF (OWASP rules) → API Gateway REST (TLS 1.3)      │
└─────────────────────┬───────────────────────────────────────────┘
                      │ VPC Link (privado)
┌─────────────────────▼───────────────────────────────────────────┐
│  VPC — Subnet Privada EKS (10.0.1.0/23)                        │
│  Kong Ingress → Order Service / Stock Adapter / etc.            │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  CDE — Subnet Privada Aislada (10.0.4.0/24) [CA-01]    │   │
│  │  RDS Aurora (KMS) | ElastiCache Redis (TLS+KMS)         │   │
│  │  Solo accesible desde subnets EKS — SG restrictivo       │   │
│  │  VPC Flow Logs habilitados (PCI-DSS Req. 10.3)          │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
│  VPC Endpoints (PrivateLink):                                   │
│  Secrets Manager | KMS | EventBridge | SQS | ECR | CloudWatch  │
│  → Tráfico AWS nunca sale a internet                            │
└─────────────────────┬───────────────────────────────────────────┘
                      │ Direct Connect / VPN (< 250ms RTT)
┌─────────────────────▼───────────────────────────────────────────┐
│  ON-PREMISE                                                     │
│  ERP | WMS | OMS | CRM (sistemas legacy protegidos)            │
│  ← Circuit Breaker + Bulkhead + Timeout en todos los ACLs       │
└─────────────────────────────────────────────────────────────────┘
                      │
┌─────────────────────▼───────────────────────────────────────────┐
│  PSP / Pasarela de Pagos (externa, TLS 1.3)                    │
│  Payment Service → solo token de pago (nunca PAN) [CA-01]      │
└─────────────────────────────────────────────────────────────────┘
```

---

## Assumptions & Open Questions

- [assumption] Los diagramas de secuencia detallados en formato Mermaid (.mmd) se producen como entregables formales en el Contract Design stage, junto con la especificación OpenAPI.
- [assumption] La máquina de estados de la orden (Diagrama 3) es el contrato de negocio que el Order Service implementa. Las transiciones de estado se validan en los tests de dominio (TDD).
