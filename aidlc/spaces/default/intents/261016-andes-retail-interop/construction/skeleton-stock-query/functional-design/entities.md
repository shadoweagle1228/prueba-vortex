## Sources

- [desc] Initial description del proyecto Andes Retail.
- [FR-01] Consulta de stock por SKU/bodega/canal.
- [CA-01] Restricción absoluta PCI-DSS y enmascaramiento PII.
- [CA-03] Clean Architecture.
- [BC-INVENTORY] Bounded Context de Inventario en domain-design components.md.
- [C-01, C-05] Contratos de inventario y reservas en contract-summary.md.

---

# Modelo de Entidades de Dominio — `skeleton-stock-query`

Este documento describe el modelo de entidades y objetos de valor (*Value Objects*) para la unidad de consulta de stock y adaptador de inventario de la PIE, implementado bajo los principios de **Clean Architecture** en Kotlin.

```yaml
entities:
  - name: StockLevel
    type: root_aggregate
    description: Representa el nivel de existencias y disponibilidad física de un SKU en un centro o bodega.
    fields:
      - name: sku
        type: SKU
        description: Identificador único del producto o ítem.
        constraints: [not_null]
      - name: warehouseId
        type: WarehouseId
        description: Identificador del almacén o centro de distribución.
        constraints: [not_null]
      - name: available
        type: integer
        description: Unidades disponibles para la venta.
        constraints: [min_0]
      - name: reserved
        type: integer
        description: Unidades bloqueadas en órdenes pendientes de cobro/despacho.
        constraints: [min_0]
      - name: lastUpdated
        type: instant
        description: Marca de tiempo de la última sincronización con la fuente de datos.
        constraints: [not_null]
      - name: source
        type: StockSource
        description: Origen de la lectura (CACHE, ERP o STALE_CACHE).
        constraints: [not_null]
    methods:
      - name: hasAvailableStock
        returns: boolean
        description: Retorna verdadero si las unidades disponibles son mayores a cero.
      - name: canReserve
        parameters: [quantity: integer]
        returns: boolean
        description: Valida si hay stock suficiente para satisfacer la cantidad solicitada.

  - name: StockReservation
    type: entity
    description: Registro temporal de bloqueo de stock asociado a una transacción de orden en curso.
    fields:
      - name: reservationId
        type: OrderId
        description: Identificador de correlación con la orden en creación.
        constraints: [not_null]
      - name: sku
        type: SKU
        constraints: [not_null]
      - name: quantity
        type: integer
        constraints: [min_1]
      - name: warehouseId
        type: WarehouseId
        constraints: [not_null]
      - name: status
        type: ReservationStatus
        constraints: [not_null]
      - name: expiresAt
        type: instant
        description: Plazo de expiración automática para liberación si el pago no concluye.
        constraints: [not_null]

value_objects:
  - name: SKU
    description: Código alfanumérico estandarizado del ítem de catálogo.
    type: string
    pattern: "^[A-Z0-9_-]{3,50}$"
    example: "SKU-COL-001"

  - name: WarehouseId
    description: Identificador canónico del centro de distribución o tienda física.
    type: string
    pattern: "^[A-Z0-9_-]{2,30}$"
    example: "WH-BOG-01"

  - name: OrderId
    description: Identificador universal único de la orden vinculada a la reserva.
    type: uuid

enums:
  - name: StockSource
    values:
      - CACHE: Obtenido de Redis dentro del TTL activo (60s).
      - ERP: Obtenido directamente del sistema SAP legacy en tiempo real.
      - STALE_CACHE: Obtenido de la copia de contingencia de Redis con Circuit Breaker abierto.

  - name: ReservationStatus
    values:
      - PENDING: Reserva creada temporalmente en memoria/Redis.
      - CONFIRMED: Bloqueo sincronizado y persistido en el ERP.
      - RELEASED: Liberada por compensación de Saga o expiración.
```

---

## Assumptions & Open Questions

- [assumption] Las entidades de dominio son puras en Kotlin, sin anotaciones de JPA, Jackson ni acoplamiento a frameworks, respetando la Dependency Rule de Clean Architecture [CA-03].
- [assumption] Los Value Objects se definen utilizando `@JvmInline value class` para garantizar tipado fuerte sin penalización de asignación en memoria en runtime.
