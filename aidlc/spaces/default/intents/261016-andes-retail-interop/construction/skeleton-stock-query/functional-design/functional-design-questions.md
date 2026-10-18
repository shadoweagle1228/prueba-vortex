## Sources

- [desc] Initial description del proyecto Andes Retail.
- [FR-01] Consulta de stock por SKU/bodega/canal.
- [FR-11] Autenticación OAuth2/OIDC multi-tenant.
- [CA-02] Backpressure y resiliencia en adaptadores legacy.
- [CA-03] Clean Architecture (domain, application, infrastructure).
- [BC-INVENTORY] Bounded Context de Inventario de domain-design components.md.
- [ADR-03] Anti-Corruption Layer (ACL) para ERP en decisions.md.
- [ADR-04] Circuit Breaker con Fail-Closed y Stale Cache en decisions.md.
- [C-01, C-05, C-09] Contratos del contract-summary.md.
- [US-01, US-08] Historias de usuario stories.md.

---

## FD-Q1. Flujo de Consulta de Stock y Jerarquía de Fuentes

¿Cuál es el algoritmo exacto para resolver una consulta `GET /v1/inventory/stock/{sku}` entre la memoria caché de Redis y el ERP on-premise?

- **Paso 1 (Caché Hit):** Consultar ElastiCache Redis con la clave `stock:{warehouseId}:{sku}`. Si existe y el TTL no ha expirado, retornar inmediatamente el dato con header `X-Stock-Source: cache` (latencia típica < 50ms).
- **Paso 2 (Caché Miss + ERP Saludable):** Si no está en caché y el Circuit Breaker (Resilience4j) está `CLOSED` o `HALF_OPEN`:
  - Enviar solicitud a través del Bulkhead (límite concurrente: 20 llamadas) al ERP on-premise con timeout de lectura de 250ms.
  - Transformar respuesta SAP en el Anti-Corruption Layer a entidad `StockLevel`.
  - Almacenar en Redis con TTL de 60 segundos.
  - Retornar con header `X-Stock-Source: erp`.
- **Paso 3 (ERP Lento o Fallido con CB Abierto):** Si el Circuit Breaker conmuta a `OPEN` o el ERP supera el timeout de 250ms:
  - Consultar en Redis la última versión conocida (stale cache, persistida con TTL extendido de contingencia de 1 hora).
  - Si existe: Retornar con código `200 OK` y header `X-Stock-Source: stale-cache`.
  - Si no existe: Retornar código `503 Service Unavailable` con header `Retry-After: 5` y esquema RFC 7807 (`ProblemDetails`).

[Answer]: Jerarquía de resolución: Caché fresco (hit) → ERP via ACL (miss) → Caché stale (degradación controlada si CB abierto) → 503 con Retry-After (si no hay caché).

---

## FD-Q2. Mapeo de Datos SAP / ACL hacia Modelo de Dominio

¿Cómo transforma el ACL los atributos propietarios de SAP en la entidad limpia de dominio `StockLevel`?

- `MATNR` (SAP Material Number) → `SKU` (Value Object)
- `WERKS` (SAP Centro / Bodega) → `WarehouseId` (Value Object)
- `LABST` (Stock de Libre Utilización) → `available` (Entero >= 0)
- `UMLMC` (Stock en Traslado / Reservado) → `reserved` (Entero >= 0)
- Cálculo de disponibilidad: `isAvailable = available > 0`.
- Regla de consistencia: Si SAP retorna valores nulos o negativos, el ACL normaliza `available = 0` y emite una advertencia de telemetría.

[Answer]: Mapeo determinista en el mapper de infraestructura SAP to Domain, aislando al dominio de tipos y nomenclaturas ABAP/SAP.

---

## FD-Q3. Gestión de Reservas Atómicas (StockPort)

¿Cómo procesa el Stock Adapter la reserva y liberación solicitada por el Order Service?

- **Reserva (`POST /internal/v1/stock/reservations`):**
  - Verifica si `available >= requestedQuantity`.
  - Si es suficiente: Actualiza temporalmente la reserva en Redis (clave `reservation:{reservationId}`) y despacha llamada al ERP para registrar bloqueo de inventario.
  - Si no es suficiente: Retorna `422 Unprocessable Entity` con detalle de SKUs no disponibles.
- **Compensación / Liberación (`DELETE /internal/v1/stock/reservations/{reservationId}`):**
  - Desbloquea el inventario en el ERP y libera la reserva en Redis de forma atómica en <= 5 segundos [FR-06].

[Answer]: Reservas atómicas mediante StockPort con rollback garantizado para soporte de Sagas transaccionales.

---

## Assumptions & Open Questions

- [assumption] El TTL del caché fresco es de 60 segundos para balancear frescura de stock y protección al ERP.
- [assumption] El stale caché se conserva hasta por 60 minutos como salvaguarda ante caídas prolongadas del enlace VPN/DirectConnect con el centro de datos on-premise.
