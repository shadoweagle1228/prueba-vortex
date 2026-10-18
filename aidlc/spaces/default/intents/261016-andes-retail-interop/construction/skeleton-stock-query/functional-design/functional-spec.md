## Sources

- [desc] Initial description del proyecto Andes Retail.
- [FR-01, FR-11] Requerimientos funcionales en requirements.md.
- [NFR-01, NFR-10, NFR-12] NFRs de latencia, CB y Bulkhead.
- [CA-02, CA-03] Restricciones de backpressure y Clean Architecture.
- [entities.md] Modelo de entidades del stage actual.
- [rules.md] Reglas de negocio del stage actual.
- [C-01, C-05, C-09] Contratos en contract-summary.md.

---

# Especificación Funcional — `skeleton-stock-query` (Walking Skeleton)

Esta especificación describe la arquitectura funcional, el flujo de procesamiento de consultas de stock, el diagrama entidad-relación y las máquinas de estado para el microservicio `stock-price-adapter`.

---

## 1. Diagrama Entidad-Relación (ER)

```mermaid
erDiagram
    StockLevel ||--o{ StockReservation : "respalda reservas de"
    StockLevel {
        SKU sku PK
        WarehouseId warehouseId PK
        int available
        int reserved
        instant lastUpdated
        StockSource source
    }
    StockReservation {
        OrderId reservationId PK
        SKU sku FK
        WarehouseId warehouseId FK
        int quantity
        ReservationStatus status
        instant expiresAt
    }
```

---

## 2. Flujo de Ejecución de Consulta de Stock

```mermaid
sequenceDiagram
    autonumber
    actor Canal as Canal Digital Externo
    participant GW as Kong Ingress / API Gateway
    participant Svc as Stock Adapter (Application Layer)
    participant Cache as ElastiCache Redis
    participant CB as Circuit Breaker (Resilience4j)
    participant ACL as ERP Anti-Corruption Layer
    participant ERP as SAP ERP on-premise

    Canal->>GW: GET /v1/inventory/stock/{sku} (Bearer JWT)
    GW->>GW: Validar JWT & Rate Limit (100 req/s)
    GW->>Svc: Despachar request con X-Correlation-ID
    Svc->>Cache: GET stock:{warehouseId}:{sku}
    alt Cache Hit (Fresco <= 60s) [BR-STK-01]
        Cache-->>Svc: Retornar StockLevel
        Svc-->>GW: 200 OK (X-Stock-Source: cache, latencia < 50ms)
        GW-->>Canal: 200 OK
    else Cache Miss
        Svc->>CB: Ejecutar llamada protegida
        alt Circuit Breaker CLOSED & Bulkhead disponible [BR-STK-02, BR-STK-05]
            CB->>ACL: Invocar consulta SAP (Timeout: 250ms)
            ACL->>ERP: RFC / BAPI Query
            alt ERP Responde Exitosamente
                ERP-->>ACL: Respuesta SAP (LABST, UMLMC)
                ACL->>ACL: Normalizar y mapear a StockLevel [BR-STK-06]
                ACL-->>CB: StockLevel
                CB-->>Svc: StockLevel
                Svc->>Cache: SET stock:{sku} (TTL 60s) & SET stale:{sku} (TTL 60m)
                Svc-->>GW: 200 OK (X-Stock-Source: erp)
                GW-->>Canal: 200 OK
            else ERP Timeout (>250ms) o Error 5xx
                ERP--xCB: Fallo o Timeout
                CB->>CB: Registrar fallo en ventana (threshold 50%)
                CB->>Cache: Fallback: GET stale:{warehouseId}:{sku} [BR-STK-03]
                alt Stale Cache Existe
                    Cache-->>CB: StockLevel Stale
                    CB-->>Svc: StockLevel Stale
                    Svc-->>GW: 200 OK (X-Stock-Source: stale-cache)
                    GW-->>Canal: 200 OK
                else Stale Cache No Existe [BR-STK-04]
                    CB-->>Svc: Error Fallback
                    Svc-->>GW: 503 Service Unavailable (Retry-After: 5)
                    GW-->>Canal: 503 Service Unavailable
                end
            end
        else Circuit Breaker OPEN
            CB->>Cache: Fallback directo a Stale Cache [BR-STK-03]
            alt Stale Cache Existe
                Cache-->>CB: StockLevel Stale
                CB-->>Svc: StockLevel Stale
                Svc-->>GW: 200 OK (X-Stock-Source: stale-cache)
                GW-->>Canal: 200 OK
            else Stale Cache No Existe
                CB-->>Svc: Error Fallback
                Svc-->>GW: 503 Service Unavailable (Retry-After: 5)
                GW-->>Canal: 503 Service Unavailable
            end
        end
    end
```

---

## 3. Máquina de Estados del Circuit Breaker

```mermaid
stateDiagram-v2
    [*] --> Closed: Inicialización del Servicio
    Closed --> Open: Tasa de error >= 50% en 30 llamadas consecutivas
    Open --> HalfOpen: Transcurridos 60 segundos de espera
    HalfOpen --> Closed: 5 llamadas consecutivas exitosas (<250ms)
    HalfOpen --> Open: Al menos 1 llamada falla o supera timeout
```

---

## 4. Interfaces de Puertos y Adaptadores (Clean Architecture)

```kotlin
// Puerto de Entrada (Application Layer)
interface GetStockUseCase {
    fun execute(sku: SKU, warehouseId: WarehouseId): StockLevel
}

// Puerto de Salida hacia Caché (Domain / Application Layer)
interface StockCachePort {
    fun get(sku: SKU, warehouseId: WarehouseId): StockLevel?
    fun getStale(sku: SKU, warehouseId: WarehouseId): StockLevel?
    fun save(stockLevel: StockLevel, ttlSeconds: Long)
}

// Puerto de Salida hacia ERP Legacy (Domain / Application Layer)
interface ErpStockPort {
    fun queryStockFromErp(sku: SKU, warehouseId: WarehouseId): StockLevel
}
```

---

## 5. Resumen de Reglas de Negocio Aplicadas

| ID Regla | Nombre | Impacto en el Flujo |
|---|---|---|
| **BR-STK-01** | Prioridad de Caché | Resuelve 90%+ de peticiones en < 50ms [NFR-01] |
| **BR-STK-02** | Circuit Breaker | Aísla caídas del ERP en ventana de 30 llamadas |
| **BR-STK-03** | Stale Cache Fallback | Garantiza disponibilidad continua con cabecera de transparencia |
| **BR-STK-04** | Fail-Closed | Responde 503 RFC 7807 sin inventar datos |
| **BR-STK-05** | Bulkhead ERP | Limita a 20 llamadas simultáneas protegiendo al ERP [NFR-12] |
| **BR-STK-06** | Normalización SAP | Elimina valores negativos o nulos provenientes de SAP |

---

## Assumptions & Open Questions

- [assumption] La implementación de Spring Boot utiliza `Resilience4j-spring-boot3` para decoradores declarativos `@CircuitBreaker` y `@Bulkhead`.
- [assumption] Las métricas del Circuit Breaker se exportan automáticamente a Prometheus mediante el módulo `micrometer-registry-prometheus`.
