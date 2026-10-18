# Smoke Test Results — `skeleton-stock-query`

## Resumen

| Entorno | Ejecutado | Fecha | Resultado global |
|---|---|---|---|
| **staging** | **Pendiente** (requiere deploy real) | — | — |
| **prod** | **Pendiente** | — | — |

> Este documento define los smoke tests a ejecutar tras cada deploy. Los resultados reales se registrarán tras cada ejecución.

---

## Suite de Smoke Tests

### 1. Health Checks (Críticos - bloqueantes)

| Test | Endpoint | Método | Esperado | Crítico |
|---|---|---|---|---|
| Liveness | `/actuator/health/liveness` | GET | `200 OK`, JSON `{"status":"UP"}` | ✅ |
| Readiness | `/actuator/health/readiness` | GET | `200 OK`, JSON `{"status":"UP","components":{"readinessState":"UP"}}` | ✅ |

**Comando**:
```bash
curl -k -s -o /dev/null -w "%{http_code}" https://api.andes-retail.example/actuator/health/liveness
curl -k -s -o /dev/null -w "%{http_code}" https://api.andes-retail.example/actuator/health/readiness
```

### 2. Autenticación y Autorización (Críticos)

| Test | Descripción | Comando | Esperado | Crítico |
|---|---|---|---|---|
| Token válido | Obtener token Keycloak con client_credentials | `curl -X POST .../token -d "grant_type=client_credentials&client_id=canal-ecommerce&client_secret=..."` | `200`, `access_token` JWT válido | ✅ |
| Endpoint con token válido | `GET /v1/inventory/stock/SMOKE` con `Authorization: Bearer $TOKEN` | `curl -H "Authorization: Bearer $TOKEN" ...` | `200`, body C-01, header `X-Stock-Source` | ✅ |
| Sin token | `GET /v1/inventory/stock/SMOKE` sin Authorization | `curl ...` | `401`, `application/problem+json`, `type=urn:andes:error:unauthorized` | ✅ |
| Token inválido | `GET` con `Authorization: Bearer invalid` | `curl -H "Authorization: Bearer invalid" ...` | `401`, problem+json | ✅ |
| Sin scope | Token `operador-cli` (solo `metrics:read`) → `GET /v1/inventory/stock/SMOKE` | `curl -H "Authorization: Bearer $TOKEN_OPERADOR" ...` | `403`, problem+json, `type=urn:andes:error:forbidden` | ✅ |

### 3. Contrato C-01 (Crítico)

| Test | Campo/Cabecera | Esperado | Crítico |
|---|---|---|---|
| Body `sku` | `sku` string | Presente, no vacío | ✅ |
| Body `available` | `available` long | Presente, ≥ 0 | ✅ |
| Body `reserved` | `reserved` long | Presente, ≥ 0 | ✅ |
| Body `lastUpdated` | `lastUpdated` ISO-8601 | Presente, válido | ✅ |
| Body `cacheHit` | `cacheHit` boolean | Presente | ✅ |
| Body `warehouseId` | `warehouseId` string | Opcional (nullable) | — |
| Header `X-Stock-Source` | `cache` \| `erp` \| `stale-cache` | Presente, valor válido | ✅ |
| Header `X-Correlation-ID` | UUID v4 | Presente, propagado | ✅ |

### 4. Resiliencia y Degradación (Importantes)

| Test | Escenario | Comando | Esperado | Crítico |
|---|---|---|---|---|
| Cache hit | 2 requests seguidos mismo SKU | 2x `curl .../stock/SKU-1` | 1º: `source=erp`, 2º: `source=cache`, `cacheHit=true` | — |
| Cache stale (simulado) | Parar SAP mock, esperar TTL fresco (60s), request | Requiere control SAP mock | `source=stale-cache`, `cacheHit=true` | — |
| Fail-closed | Parar SAP mock + borrar stale key Redis | Requiere control Redis/SAP | `503`, `Retry-After: 5`, `type=urn:andes:error:stock-unavailable` | — |
| Circuit Breaker | 10+ fallos consecutivos SAP | Load test | CB abre → subsequent requests 503 sin llamar SAP | — |

### 5. Rate Limiting (NFR-11)

| Test | Escenario | Esperado | Crítico |
|---|---|---|---|
| Normal | 100 req/s mismo consumer | Todos `200` | ✅ |
| Exceso | 101 req/s mismo consumer | 100 `200`, 1 `429` con `Retry-After` | ✅ |
| Headers rate limit | Response headers | `X-RateLimit-Limit: 100`, `X-RateLimit-Remaining`, `Retry-After` en 429 | — |

### 6. Métricas y Observabilidad

| Test | Endpoint | Esperado | Crítico |
|---|---|---|---|
| Prometheus scrape | `/actuator/prometheus` con bearer token | `200`, métricas en formato Prometheus | ✅ |
| CB metrics | `resilience4j_circuitbreaker_state{name="sapStockCircuitBreaker"}` | `0` (CLOSED) | — |
| Bulkhead metrics | `resilience4j_bulkhead_available_concurrent_calls{name="sapStockBulkhead"}` | `≥ 15` (de 20) | — |
| JVM metrics | `jvm_memory_used_bytes`, `jvm_threads_live` | Presentes | — |

### 7. Correlation ID (NFR-08)

| Test | Escenario | Esperado | Crítico |
|---|---|---|---|
| Header entrante propagado | `curl -H "X-Correlation-ID: test-123" ...` | Response header `X-Correlation-ID: test-123` | ✅ |
| Header generado | Request sin `X-Correlation-ID` | Response header `X-Correlation-ID: <uuid-v4>` | ✅ |
| En problem+json | Error 401/403/503 | Body `correlationId` = header value | ✅ |
| Longitud max | Header 200 chars | Truncado a 128 chars | — |

---

## Plantilla de resultados (para llenar tras ejecución)

| Test ID | Nombre | Resultado | Detalles | Timestamp |
|---|---|---|---|---|
| ST-01 | Liveness | ⏳ Pendiente | | |
| ST-02 | Readiness | ⏳ Pendiente | | |
| ST-03 | Token válido | ⏳ Pendiente | | |
| ST-04 | Endpoint auth | ⏳ Pendiente | | |
| ST-05 | Sin token → 401 | ⏳ Pendiente | | |
| ST-06 | Token inválido → 401 | ⏳ Pendiente | | |
| ST-07 | Sin scope → 403 | ⏳ Pendiente | | |
| ST-08 | Body C-01 completo | ⏳ Pendiente | | |
| ST-09 | Header X-Stock-Source | ⏳ Pendiente | | |
| ST-10 | Header X-Correlation-ID | ⏳ Pendiente | | |
| ST-11 | Cache hit | ⏳ Pendiente | | |
| ST-12 | Rate limit 100 req/s | ⏳ Pendiente | | |
| ST-13 | Rate limit 101 req/s → 429 | ⏳ Pendiente | | |
| ST-14 | Prometheus scrape | ⏳ Pendiente | | |
| ST-15 | CB metrics CLOSED | ⏳ Pendiente | | |
| ST-16 | Correlation ID propagado | ⏳ Pendiente | | |
| ST-17 | Correlation ID generado | ⏳ Pendiente | | |
| ST-18 | Correlation ID en problem+json | ⏳ Pendiente | | |

---

## Criterios de Go/No-Go

| Criterio | Go | No-Go |
|---|---|---|
| **Todos los tests Críticos (✅)** | PASS | Cualquier FAIL → **No-Go**, rollback inmediato |
| Tests Importantes (—) | ≥ 80% PASS | < 80% → Evaluar riesgo, documentar deuda |
| Tiempo total smoke tests | < 5 min | > 10 min → Investigar performance |

---

## Registro de ejecuciones

| Fecha | Entorno | Ejecutor | Tests corridos | Pass | Fail | Decisión |
|---|---|---|---|---|---|---|
| — | staging | — | — | — | — | — |
| — | prod | — | — | — | — | — |