# Cross-Unit Traceability — `skeleton-stock-query`

## Unidad: `skeleton-stock-query` (Walking Skeleton)

Esta unidad es la primera y única del scope actual. No hay otras unidades completadas con las que trazar dependencias cruzadas.

## Trazabilidad interna (requirements → code → tests)

| Requirement | Tipo | Implementación | Tests |
|---|---|---|---|
| FR-01 | Consulta stock por SKU | `StockController.GET /v1/inventory/stock/{sku}` | `StockControllerTest`, `StockAdapterIntegrationTest` |
| FR-11 | Auth OAuth2/OIDC multi-tenant (base) | `SecurityConfig` + Keycloak Helm | `StockControllerTest` (401/403), `StockPriceAdapterApplicationTests` |
| NFR-01 | Latencia p95 ≤ 300ms | Caché fresca <15ms, ERP 250ms, CB/Bulkhead | `GetStockServiceTest`, `SapClientTest` (timeout), `performance-test-instructions.md` |
| NFR-04 | Disponibilidad 99.9% | Multi-AZ, PDB, HPA, CB fail-closed | `build-and-test-summary.md` (deuda: no verificado en CI) |
| NFR-06 | TLS 1.3 + KMS | `infra/data_stack.py` (Redis TLS, Aurora KMS) | `security-test-instructions.md` (no automatizado) |
| NFR-10 | Circuit Breaker ERP | `SapStockAdapter` + `application.yml` CB config | `StockPriceAdapterApplicationTests` (parámetros efectivos) |
| NFR-11 | Rate limiting por canal | `deploy/kong/kong-plugin-ratelimit.yaml` | `security-test-instructions.md` (no automatizado) |
| NFR-12 | Bulkhead ERP | `SapStockAdapter` + `application.yml` bulkhead config | `StockPriceAdapterApplicationTests` (parámetros efectivos) |
| CA-01 | PCI-DSS base | CDE en `infra/network_stack.py`, logs sanitizados | `security-test-instructions.md` |
| CA-02 | Backpressure legacy | CB + Bulkhead en `SapStockAdapter` | `StockPriceAdapterApplicationTests` |
| CA-03 | Clean Architecture | `ArchitectureTest` (ArchUnit) | `ArchitectureTest` |
| US-01 | Consultar stock disponible | `GET /v1/inventory/stock/{sku}` end-to-end | `StockAdapterIntegrationTest`, `StockControllerTest` |
| US-08 | Auth multi-tenant (base) | Keycloak realm + 3 clients + scopes | `StockControllerTest` (scopes) |
| BR-STK-01 | Prioridad caché fresca | `GetStockService` → `findFresh` primero | `GetStockServiceTest` (acierto fresco) |
| BR-STK-02 | Circuit Breaker ERP | `@CircuitBreaker` en `SapStockAdapter` | `GetStockServiceTest` (degradación), `StockPriceAdapterApplicationTests` |
| BR-STK-03 | Degradación a stale | `GetStockService` → `findStale` en fallback | `GetStockServiceTest` (stale-cache, fail-closed) |
| BR-STK-04 | 503 + Retry-After | `GlobalExceptionHandler` + `ErpStockUnavailableException` | `GlobalExceptionHandlerTest`, `StockControllerTest` |
| BR-STK-05 | Bulkhead ERP | `@Bulkhead` en `SapStockAdapter` (20/100ms) | `StockPriceAdapterApplicationTests` |
| BR-STK-06 | Normalización negativos | `SapStockMapper` (cota reservado ≤ disponible) | `SapStockMapperTest` |

## Dependencias de otras unidades (futuras)

| Unidad futura | Dependencia | Estado |
|---|---|---|
| `stock-reservation` (FR-06) | `StockReservation` entidad existe, flujo pendiente | `Deferred` |
| `order-saga` | Compensación de reserva | `Deferred` |
| `multi-tenant-full` (FR-11 completo) | Realm base entregado, federación pendiente | `Partial` |
| `observability` | Métricas expuestas, dashboards/alertas pendientes | `Deferred` |

## Matriz de cobertura (resumen)

| Capa | Requirements cubiertos | Tests | Cobertura líneas |
|---|---|---|---|
| Dominio | FR-01, BR-STK-06, CA-03 | 7 | 98,0 % |
| Aplicación | FR-01, BR-STK-01/03/04, CA-02 | 6 | 100,0 % |
| Infra (caché) | NFR-01, BR-STK-01/03 | 8 | 98,3 % |
| Infra (ERP/ACL) | NFR-01/10/12, BR-STK-02/05/06 | 15 | 94,4 % |
| Web (API/Seguridad) | FR-01/11, NFR-08, CA-01 | 14 | 94,3 % |
| Arquitectura | CA-03 | 2 | 100,0 % |
| Integración | FR-01, US-01, BR-STK-01/03/04 | 2 | — |
| Contexto completo | FR-01/11, NFR-01/04/10/12, CA-01/02/03 | 4 | — |

**Total**: 58 tests, **95,30 % líneas**, 0 fallos