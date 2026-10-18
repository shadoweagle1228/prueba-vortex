## Sources

- [desc] Initial description del proyecto.
- [scope] Scope document y intent backlog.
- [FR-01] a [FR-12] del requirements.md.
- [CA-01] PCI-DSS — CDE, tokenización, enmascaramiento PII.
- [CA-02] Backpressure en adaptadores legacy.
- [CA-03] Clean Architecture — domain/application/infrastructure.
- [TF-stack] Kotlin + Spring Boot.

---

## Domain Design — Bounded Contexts y Componentes

### Mapa de Bounded Contexts

```
┌─────────────────────────────────────────────────────────────────────┐
│                    PLATFORM OF INTEGRATION & EVENTS (PIE)           │
│                                                                     │
│  ┌──────────────────┐    ┌──────────────────┐                       │
│  │  ORDER CONTEXT   │    │ INVENTORY CONTEXT │                       │
│  │  (Order Service) │    │ (Stock Adapter)   │                       │
│  │                  │    │                  │                       │
│  │ Order            │    │ StockLevel        │                       │
│  │ OrderItem        │◄───│ Price             │                       │
│  │ OrderStatus      │    │ Reservation       │                       │
│  │ PaymentToken     │    │                  │                       │
│  └──────────┬───────┘    └──────────┬───────┘                       │
│             │                       │                               │
│             │ Domain Events         │ ACL → ERP [CA-02]             │
│             ▼                       ▼                               │
│  ┌──────────────────────────────────────────────────────────┐      │
│  │              EVENT BUS (Amazon EventBridge)               │      │
│  │  order.confirmed | payment.approved | payment.rejected    │      │
│  │  inventory.reserved | inventory.released                  │      │
│  └──────────────────────┬───────────────────────────────────┘      │
│                          │                                          │
│  ┌───────────────┐  ┌────▼──────────────┐  ┌────────────────────┐ │
│  │ PAYMENT CTX   │  │ NOTIFICATION CTX   │  │  CATALOG CONTEXT   │ │
│  │(Payment Svc)  │  │(Notification Svc)  │  │  (Catalog Svc)     │ │
│  │               │  │                   │  │                    │ │
│  │ PaymentToken  │  │ Webhook           │  │ Product            │ │
│  │ ChargeResult  │  │ NotificationEvent │  │ Price              │ │
│  │ [CA-01] No PAN│  │ DeliveryAttempt   │  │ PriceList          │ │
│  └───────────────┘  └───────────────────┘  └────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘

Contextos legacy (fuera de los límites de la PIE — acceso solo via ACL):
  ERP Context: StockLevel, Price, Product (fuente de verdad)
  WMS Context: FulfillmentOrder, PickingOrder (mes 3)
  OMS Context: LegacyOrder (mes 3)
  CRM Context: Customer, LoyaltyProfile (mes 3)
```

---

### Bounded Context 1: ORDER CONTEXT

**Responsabilidad:** Gestionar el ciclo de vida completo de una orden desde su creación hasta la entrega. Es el contexto orquestador del flujo principal del negocio.

**Agregado raíz:** `Order`

#### Entidades

```kotlin
// Agregado raíz — todas las modificaciones van por aquí
data class Order(
    val id: OrderId,                    // Value Object — UUID
    val tenantId: TenantId,             // Value Object — multi-tenant
    val channelId: ChannelId,           // Value Object
    val customerId: CustomerId,         // Value Object — no PII directa
    val items: List<OrderItem>,         // Entidad
    val shippingAddress: Address,       // Value Object — enmascarado en logs [CA-01]
    val paymentToken: PaymentToken,     // Value Object — solo token, no PAN [CA-01]
    val status: OrderStatus,            // Sealed class (estado de la máquina)
    val totalAmount: Money,             // Value Object — centavos COP
    val idempotencyKey: IdempotencyKey, // Value Object
    val correlationId: CorrelationId,   // Value Object — trazabilidad
    val createdAt: Instant,
    val updatedAt: Instant
) {
    // Métodos de dominio — las reglas de negocio viven aquí
    fun confirm(chargeId: ChargeId): Order
    fun rejectPayment(declineCode: DeclineCode): Order
    fun cancel(reason: CancellationReason): Order
}

data class OrderItem(
    val id: OrderItemId,
    val sku: SKU,
    val quantity: Quantity,
    val unitPrice: Money
)
```

#### Value Objects

```kotlin
@JvmInline value class OrderId(val value: UUID)
@JvmInline value class TenantId(val value: String)
@JvmInline value class ChannelId(val value: String)
@JvmInline value class CustomerId(val value: String)
@JvmInline value class SKU(val value: String)
@JvmInline value class IdempotencyKey(val value: UUID)
@JvmInline value class CorrelationId(val value: UUID)

// Dinero en centavos — evita errores de punto flotante
data class Money(val amount: Long, val currency: Currency) {
    init { require(amount >= 0) { "El monto no puede ser negativo" } }
}

// Token de pago — NUNCA el PAN [CA-01]
@JvmInline value class PaymentToken(val value: String) {
    // Redacta el token en toString() para logs seguros
    override fun toString() = "PaymentToken(${value.take(8)}***REDACTED***)"
}

// Dirección — redactada en logs [CA-01]
data class Address(
    val street: String,
    val city: String,
    val department: String,
    val postalCode: String,
    val country: String = "CO"
) {
    // Enmascarado para logs — solo ciudad visible
    fun toLogSafe() = "Address(city=${city}, country=${country})"
    override fun toString() = toLogSafe()
}
```

#### Estados del Dominio

```kotlin
sealed class OrderStatus {
    data object Created : OrderStatus()
    data object Confirmed : OrderStatus()
    data object InProgress : OrderStatus()
    data object Shipped : OrderStatus()
    data object Delivered : OrderStatus()
    data class Rejected(val reason: String) : OrderStatus()
    data class PaymentFailed(val declineCode: DeclineCode) : OrderStatus()
    data class Cancelled(val reason: CancellationReason) : OrderStatus()
}
```

#### Eventos de Dominio

```kotlin
sealed class OrderDomainEvent {
    data class OrderCreated(val orderId: OrderId, val tenantId: TenantId, val items: List<OrderItem>, val correlationId: CorrelationId) : OrderDomainEvent()
    data class OrderConfirmed(val orderId: OrderId, val chargeId: ChargeId) : OrderDomainEvent()
    data class OrderPaymentFailed(val orderId: OrderId, val declineCode: DeclineCode) : OrderDomainEvent()
    data class OrderCancelled(val orderId: OrderId, val reason: CancellationReason) : OrderDomainEvent()
}
```

#### Puertos (interfaces de dominio)

```kotlin
// Puerto de salida — implementado en infrastructure
interface OrderRepository {
    fun save(order: Order): Order
    fun findById(id: OrderId): Order?
    fun findByIdempotencyKey(key: IdempotencyKey): Order?
}

interface StockPort {
    fun reserve(sku: SKU, quantity: Quantity, reservationId: OrderId): StockReservation
    fun release(reservationId: OrderId): Unit
}

interface PaymentPort {
    fun authorize(token: PaymentToken, amount: Money, orderId: OrderId): PaymentResult
}

interface EventPublisherPort {
    fun publish(event: OrderDomainEvent): Unit
}
```

---

### Bounded Context 2: INVENTORY CONTEXT

**Responsabilidad:** Consultar stock y precios, gestionar reservas de inventario, y proteger el ERP de sobrecargas mediante el ACL. [CA-02]

**Agregado raíz:** `StockLevel`

#### Entidades y Value Objects

```kotlin
data class StockLevel(
    val sku: SKU,
    val warehouseId: WarehouseId,
    val available: Int,
    val reserved: Int,
    val lastUpdated: Instant,
    val source: StockSource  // CACHE | ERP | STALE_CACHE
)

data class StockReservation(
    val reservationId: OrderId,
    val sku: SKU,
    val quantity: Quantity,
    val status: ReservationStatus
)

data class Price(
    val sku: SKU,
    val channelId: ChannelId,
    val customerSegment: CustomerSegment,
    val amount: Money,
    val promotionalAmount: Money?,
    val promotionExpiry: Instant?,
    val priceListId: PriceListId
)

sealed class StockSource {
    data object Cache : StockSource()
    data object ERP : StockSource()
    data object StaleCache : StockSource()  // CB abierto
}
```

#### Puertos (interfaces de dominio)

```kotlin
// Puerto de entrada hacia el ERP — implementado con CB + Bulkhead [CA-02]
interface ErpStockPort {
    fun queryStock(sku: SKU, warehouseId: WarehouseId): StockLevel
    fun reserveStock(reservation: StockReservation): StockReservation
    fun releaseReservation(reservationId: OrderId): Unit
}

interface ErpPricePort {
    fun queryPrice(sku: SKU, channelId: ChannelId, segment: CustomerSegment): Price
}

// Puerto de cache
interface StockCachePort {
    fun get(sku: SKU, warehouseId: WarehouseId): StockLevel?
    fun put(stock: StockLevel, ttl: Duration): Unit
    fun invalidate(sku: SKU): Unit
}
```

---

### Bounded Context 3: PAYMENT CONTEXT

**Responsabilidad:** Autorizar pagos con tokens del PSP. **Nunca maneja PANs.** [CA-01]

#### Entidades y Value Objects

```kotlin
data class PaymentAuthorization(
    val orderId: OrderId,
    val token: PaymentToken,
    val amount: Money,
    val result: PaymentResult,
    val correlationId: CorrelationId
)

sealed class PaymentResult {
    data class Authorized(val chargeId: ChargeId, val last4: String) : PaymentResult()
    data class Declined(val declineCode: DeclineCode, val message: String) : PaymentResult()
}

@JvmInline value class ChargeId(val value: String)
@JvmInline value class DeclineCode(val value: String)
```

---

### Bounded Context 4: NOTIFICATION CONTEXT

**Responsabilidad:** Entregar webhooks a canales externos de forma confiable, con reintentos y DLQ.

#### Entidades y Value Objects

```kotlin
data class WebhookDelivery(
    val id: WebhookDeliveryId,
    val tenantId: TenantId,
    val eventType: WebhookEventType,
    val payload: WebhookPayload,        // payload sin PII [CA-01]
    val endpoint: WebhookEndpoint,
    val attempt: Int,
    val status: DeliveryStatus,
    val signature: HmacSignature        // firma HMAC del payload
)

sealed class WebhookEventType {
    data object OrderConfirmed : WebhookEventType()
    data object PaymentApproved : WebhookEventType()
    data object PaymentRejected : WebhookEventType()
    data object ShipmentStarted : WebhookEventType()   // mes 3
    data object DeliveryConfirmed : WebhookEventType() // mes 3
}

sealed class DeliveryStatus {
    data object Pending : DeliveryStatus()
    data object Delivered : DeliveryStatus()
    data class Failed(val attempt: Int) : DeliveryStatus()
    data object DeadLetter : DeliveryStatus()
}
```

---

## Assumptions & Open Questions

- [assumption] Los Value Objects usan Kotlin inline classes (`@JvmInline value class`) para zero-overhead en runtime manteniendo type safety. Esto es un detalle de implementación; la semántica de dominio es lo que importa en este artefacto.
- [assumption] El CATALOG CONTEXT (consulta de precios y catálogo de productos) comparte el mismo servicio que el INVENTORY CONTEXT (Stock Adapter) en el MVP por simplicidad operacional. Se puede separar en mes 3 si la carga o la evolución del dominio lo justifica.
- [assumption] Los contextos WMS, OMS y CRM son scope de mes 3. Sus entidades y Value Objects se definen en ese sprint.
