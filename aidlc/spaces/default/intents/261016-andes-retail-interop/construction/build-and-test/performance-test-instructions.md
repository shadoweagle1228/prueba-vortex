# Instrucciones de Pruebas de Rendimiento — `skeleton-stock-query`

## Objetivos de rendimiento (NFR)

| ID | Requisito | Presupuesto |
|---|---|---|
| NFR-01 | Latencia p95 consulta stock | ≤ 300 ms |
| NFR-04 | Disponibilidad | 99,9 % |
| BR-STK-01 | Acierto caché fresco | < 15 ms |
| BR-STK-02 | Timeout ERP (SAP) | 250 ms (conexión 100 ms) |
| BR-STK-05 | Bulkhead ERP | Máx. 20 concurrentes, espera 100 ms |

## Estrategia

Las pruebas de rendimiento **no se ejecutan en el pipeline CI local** (requieren entorno representativo). Se definen aquí para ejecución en entorno de staging/producción.

## Herramientas recomendadas

- **k6** o **Gatling** para pruebas de carga HTTP
- **Prometheus + Grafana** para observabilidad en tiempo real
- **Jaeger/Zipkin** para trazabilidad distribuida (correlation-id)

## Escenarios de carga

### 1. Smoke test (rápido, < 2 min)
```bash
k6 run --vus 10 --duration 30s \
  -e BASE_URL=https://api.andes-retail.example \
  -e TOKEN=<access_token_canal_ecommerce> \
  scripts/stock-smoke.js
```
- Verifica latencia p95 < 300 ms bajo carga ligera
- Valida cabeceras `X-Stock-Source` y `X-Correlation-ID`

### 2. Carga sostenida (15 min)
```bash
k6 run --vus 100 --duration 15m \
  -e BASE_URL=https://api.andes-retail.example \
  -e TOKEN=<access_token_canal_ecommerce> \
  scripts/stock-load.js
```
- Objetivo: p95 < 300 ms, tasa de error < 0,1 %
- Verifica que Circuit Breaker no abre bajo carga normal
- Verifica que Bulkhead limita a 20 concurrentes hacia ERP

### 3. Pico de tráfico (rate limiting Kong)
```bash
k6 run --vus 200 --duration 5m \
  -e BASE_URL=https://api.andes-retail.example \
  -e TOKEN=<access_token_canal_ecommerce> \
  scripts/stock-spike.js
```
- Kong debe rechazar > 100 req/s por consumer con 429
- Verificar `Retry-After` en respuesta

### 4. Degradación controlada (ERP lento/caído)
```bash
k6 run --vus 50 --duration 10m \
  -e BASE_URL=https://api.andes-retail.example \
  -e TOKEN=<access_token_canal_ecommerce> \
  -e SAP_LATENCY_MS=2000 \
  scripts/stock-degradation.js
```
- Verificar fallback a `stale-cache` (cabecera `X-Stock-Source: stale-cache`)
- Verificar 503 con `Retry-After: 5` cuando no hay stale
- Circuit Breaker debe abrir tras umbral (50 % fallos en 30 llamadas)

## Métricas a observar

| Métrica | PromQL | Umbral alerta |
|---|---|---|
| Latencia p95 | `histogram_quantile(0.95, rate(http_server_duration_seconds_bucket{uri="/v1/inventory/stock/*"}[5m]))` | > 0.3s |
| Tasa error 5xx | `rate(http_server_requests_seconds_count{status=~"5.."}[5m])` | > 0.001 |
| Circuit Breaker estado | `resilience4j_circuitbreaker_state{name="sapStockCircuitBreaker"}` | ≠ 0 (CLOSED) |
| Bulkhead uso | `resilience4j_bulkhead_available_concurrent_calls{name="sapStockBulkhead"}` | < 5 (cerca de límite 20) |
| Cache hit ratio | `rate(stock_cache_hits_total[5m]) / rate(stock_cache_requests_total[5m])` | > 0.8 |

## Entorno de ejecución representativo

- EKS 1.29, NodeGroup `c7g.medium` (Graviton3) 2-6 nodos
- ElastiCache Redis `cache.t4g.medium` Multi-AZ TLS
- Aurora PostgreSQL 16 Serverless v2
- Kong Ingress Controller (rate limiting)
- Keycloak 24 (HA)

## No verificado en CI local

- Latencia real de red (localhost ≠ cross-AZ)
- Comportamiento bajo presión de GC en JVM
- Failover de Redis/Aurora
- Rate limiting real de Kong (requiere consumer real)