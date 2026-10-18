# Load Test Plan — `skeleton-stock-query`

## Referencias upstream

| Artefacto | Ubicación | Qué aporta |
|---|---|---|
| `performance-design` | `construction/skeleton-stock-query/nfr-design/performance-design.md` | Latencia budgets, throughput, capacity |
| `scalability-design` | `construction/skeleton-stock-query/nfr-design/scalability-design.md` | HPA, scaling policies, bottlenecks |
| `dashboards.md` | `operation/observability-setup/dashboards.md` | Golden signals, métricas a validar |
| `slo-config.md` | `operation/observability-setup/slo-config.md` | SLOs, SLIs, error budgets |
| `reliability-design` | `construction/skeleton-stock-query/nfr-design/reliability-design.md` | CB/Bulkhead, degradación controlada |

---

## 1. Objetivos de Validación

| Objetivo | Métrica | Target | Fuente |
|---|---|---|---|
| **Latencia p95** | `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/v1/inventory/stock/**"}[5m]))` | **≤ 300 ms** | NFR-01, SLO-STOCK-LATENCY |
| **Latencia p99** | `histogram_quantile(0.99, ...)` | **≤ 500 ms** | Performance-design |
| **Throughput sostenido** | `rate(http_server_requests_seconds_count{uri="/v1/inventory/stock/**"}[5m])` | **≥ 500 RPS** | Scalability-design |
| **Throughput pico** | Same | **≥ 1000 RPS** (burst 30s) | Scalability-design |
| **Error Rate** | `rate(http_server_requests_seconds_count{status=~"5.."}[5m])` | **< 0.1%** | SLO-STOCK-AVAILABILITY (99.9%) |
| **Cache Hit Ratio** | `rate(stock_cache_hits_total{source="cache"}[5m]) / rate(stock_cache_requests_total[5m])` | **≥ 80%** | BR-STK-01, SLO-STOCK-CACHE-HIT |
| **Degradación tiempo** | `rate(stock_queries_total{source="stale-cache"}[5m]) + rate(stock_fail_closed_total[5m]) / rate(stock_queries_total[5m])` | **< 1%** | BR-STK-03/04, SLO-STOCK-DEGRADATION |
| **Circuit Breaker** | `resilience4j_circuitbreaker_state{sapStockCircuitBreaker}` | **0 (CLOSED)** estado normal | NFR-10, BR-STK-02 |
| **Bulkhead Utilization** | `1 - (resilience4j_bulkhead_available_concurrent_calls / 20)` | **< 50%** normal, **< 80%** bajo carga | NFR-12, BR-STK-05 |
| **Auto-scaling** | `kube_hpa_status_desired_replicas` | Scale-out < 3 min, Scale-in < 10 min | Scalability-design |

---

## 2. Perfiles de Tráfico

### 2.1 Estado Estable (Steady State)
| Parámetro | Valor | Justificación |
|---|---|---|
| **RPS base** | 200 RPS | Tráfico típico ecommerce/tienda |
| **Distribución SKU** | Zipf (α=1.2), 10K SKUs activos | 80/20 rule: 20% SKUs = 80% tráfico |
| **Canales** | 60% ecommerce, 30% tienda, 10% operador | Distribución real |
| **Cache Hit Ratio esperado** | 85% | TTL 60s, tráfico recurrente |
| **Duración** | 30 min | Estado estable representativo |

### 2.2 Hora Pico (Peak Hour)
| Parámetro | Valor | Justificación |
|---|---|---|
| **RPS pico** | 800 RPS | 4x base (Black Friday, promociones) |
| **Duración pico** | 2 horas | Ventana promocional típica |
| **Rampa subida** | 10 min (200 → 800 RPS) | Inicio promoción |
| **Rampa bajada** | 15 min (800 → 200 RPS) | Fin promoción |
| **Cache Hit Ratio** | 75% (más SKUs fríos) | Más SKUs únicos en pico |

### 2.3 Burst / Stress (Más allá de capacidad)
| Parámetro | Valor | Objetivo |
|---|---|---|
| **RPS máximo** | 1500 RPS (2x pico) | Encontrar punto de quiebre |
| **Duración** | 5 min | Burst corto |
| **Objetivo** | Identificar: punto saturación CB, bulkhead limit, HPA max, error rate | |

### 2.4 Soak Test (Estabilidad Prolongada)
| Parámetro | Valor | Objetivo |
|---|---|---|
| **RPS** | 300 RPS (1.5x base) | Carga sostenida |
| **Duración** | 4 horas | Detectar memory leaks, connection leaks, GC pressure |
| **Métricas clave** | RSS memory, GC frequency, connection pool usage, CPU | Estabilidad prolongada |

---

## 3. Escenarios de Prueba

| Escenario | Descripción | Métricas Clave | Criterio PASS |
|---|---|---|---|
| **TC-01: Baseline Steady** | 200 RPS, 30 min, Zipf SKU | p95, RPS, Error Rate, Cache Hit | p95 ≤ 300ms, Error < 0.1%, Cache Hit ≥ 85% |
| **TC-02: Peak Hour** | Rampa 200→800 RPS (10m), hold 2h, ramp down | p95, HPA scale, Error Rate, CB state | p95 ≤ 300ms, HPA scale < 3m, Error < 0.1%, CB=CLOSED |
| **TC-03: Burst Stress** | 1500 RPS, 5 min | Breaking point, CB open, Bulkhead reject, Error rate | Identificar breaking point, CB protege, bulkhead < 20 available |
| **TC-04: Soak Test** | 300 RPS, 4h | Memory, GC, Connections, CPU, p95 drift | Sin memory leak, p95 estable ±10%, sin connection leak |
| **TC-05: Cache Miss Storm** | 500 RPS SKUs únicos (cold cache) | Cache miss → ERP, CB behavior, p95 | p95 ≤ 500ms (cache miss), CB=CLOSED, bulkhead OK |
| **TC-04: SAP Degradation** | SAP mock latency 500ms → 2s | CB open/half-open, fallback stale, fail-closed | CB opens at threshold, stale served, fail-closed 503 |
| **TC-05: Redis Failover** | Trigger failover ElastiCache | App degradation, stale-cache, recovery | < 60s failover, stale served, cache repoblado < 5m |
| **TC-06: Rate Limiting** | 101 req/s mismo consumer | 429 rate, headers, otros consumers unaffected | 100 req/s = 200, 101 = 429, otros consumers 200 |

---

## 4. Configuración de Ejecución

### Herramienta: k6 (recomendado) / Gatling / Locust

```javascript
// k6 script skeleton (load-test.js)
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics
const stockLatency = new Trend('stock_latency');
const stockErrors = new Rate('stock_errors');
const cacheHit = new Rate('cache_hit');
const stockSource = new Counter('stock_source');

export const options = {
  stages: [
    { duration: '10m', target: 800 },  // Rampa peak
    { duration: '2h', target: 800 },   // Hold peak
    { duration: '15m', target: 200 },  // Ramp down
  ],
  thresholds: {
    'http_req_duration{p95}': ['p(95)<300'],
    'http_req_failed': ['rate<0.001'],
    'stock_errors': ['rate<0.001'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'https://api.andes-retail.example';
const TOKEN = __ENV.TOKEN;

const SKUS = [
  // 10K SKUs con distribución Zipf
  ...Array.from({length: 10000}, (_, i) => `SKU-${String(i+1).padStart(5,'0')}`)
];

function zipfSample(arr, alpha=1.2) {
  // Implementación simple Zipf
  const n = arr.length;
  const weights = Array.from({length: n}, (_, i) => 1 / Math.pow(i+1, alpha));
  const total = weights.reduce((a,b) => a+b, 0);
  const rand = Math.random() * total;
  let sum = 0;
  for (let i = 0; i < n; i++) {
    sum += weights[i];
    if (rand < sum) return arr[i];
  }
  return arr[n-1];
}

export default function() {
  const sku = zipfSample(SKUS);
  const url = `${BASE_URL}/v1/inventory/stock/${sku}`;
  
  const params = {
    headers: {
      'Authorization': `Bearer ${TOKEN}`,
      'X-Correlation-ID': `k6-${__VU}-${__ITER}`,
    },
  };
  
  const res = http.get(url, params);
  
  stockLatency.add(res.timings.duration);
  stockErrors.add(res.status >= 500);
  
  check(res, {
    'status 200': (r) => r.status === 200,
    'body has sku': (r) => r.json('sku') !== undefined,
    'header X-Stock-Source': (r) => r.headers['X-Stock-Source'] !== undefined,
    'cacheHit in body': (r) => r.json('cacheHit') !== undefined,
  });
  
  const source = res.headers['X-Stock-Source'];
  if (source) stockSource.add(1, {source});
  
  const cacheHitVal = res.json('cacheHit');
  if (cacheHitVal !== undefined) cacheHit.add(cacheHitVal === true);
  
  sleep(0.1); // Think time
}

export function handleSummary(data) {
  return {
    'stdout': textSummary(data, { indent: ' ', enableColors: true }),
    'summary.json': JSON.stringify(data),
  };
}
```

---

## 5. Entorno de Ejecución

### Requisitos Previos
| Requisito | Detalle |
|---|---|
| **Entorno** | Staging (réplica prod: EKS, Redis, Aurora, Kong, Keycloak) |
| **Datos** | 10K SKUs precargados en Redis (fresco) + SAP mock |
| **Token** | `canal-ecommerce` client credentials (Keycloak) |
| **Rate Limiting** | Desactivado para pruebas de carga (o límite alto 10000 req/s) |
| **Red** | k6 runners en misma VPC (baja latencia red) |
| **Observabilidad** | Prometheus + Grafana + X-Ray activos |

### Runners k6
| Configuración | Valor |
|---|---|
| **Runners** | 3 instancias c6g.xlarge (4 vCPU, 8GB) en misma AZ |
| **VUs totales** | 400 (peak 800 RPS / 0.5 RPS por VU con think time) |
| **Coordinación** | k6 cloud o archivo shared state para distribución SKUs |

---

## 6. Métricas de Validación y Análisis

### Dashboard Tiempo Real (Grafana)
- Panel: `AndesRetail/StockAdapter/GoldenSignals`
- Alertas: Ver `alarms.md`

### Análisis Post-Ejecución
| Análisis | Herramienta | Output |
|---|---|---|
| Percentiles latencia | k6 summary / Grafana | p50, p90, p95, p99, max |
| Throughput vs Latencia | Grafana scatter | Curva capacidad |
| Error Rate breakdown | Logs + Métricas | 5xx por tipo (CB, bulkhead, timeout, SAP) |
| Cache Hit Ratio | Prometheus | Time series + heatmap por SKU |
| HPA Behavior | k8s events + métricas | Scale events vs RPS |
| CB/Bulkhead Events | Prometheus + Logs | Timeline eventos |
| Resource Utilization | CloudWatch / Container Insights | CPU, Mem, Network, Disk |

---

## 7. Criterios de Aceptación (Go/No-Go)

| Criterio | Go | No-Go (Blocker) |
|---|---|---|
| **TC-01 Baseline** | p95 ≤ 300ms, Error < 0.1%, Cache Hit ≥ 85% | Cualquier fallo |
| **TC-02 Peak** | p95 ≤ 300ms, HPA scale < 3m, Error < 0.1%, CB=CLOSED | p95 > 300ms, Error ≥ 0.1%, CB open |
| **TC-03 Burst** | Breaking point identificado, CB protege, bulkhead rechaza | App crashea, data loss, CB no protege |
| **TC-04 Soak** | Memoria estable ±10%, p95 estable ±10%, sin leaks | Memory leak, p95 drift > 20%, connection leak |
| **TC-05 Cache Miss** | p95 ≤ 500ms (miss), CB=CLOSED, bulkhead OK | p95 > 1s, CB open, bulkhead exhausted |
| **TC-06 SAP Degradation** | CB abre en threshold, stale servido, fail-closed 503 | CB no abre, bulkhead exhausted, app crashea |
| **TC-07 Redis Failover** | < 60s failover, stale servido, cache repoblado < 5m | Failover > 2min, data loss, app crashea |
| **TC-08 Rate Limit** | 100 req/s = 200, 101 = 429, headers correctos | Rate limit no aplica, 429 incorrecto |

---

## 8. Entregables

| Entregable | Formato | Ubicación |
|---|---|---|
| **Load Test Plan** | Markdown | `operation/performance-validation/load-test-plan.md` (este doc) |
| **Test Results** | JSON + Markdown | `operation/performance-validation/test-results.md` |
| **NFR Validation Matrix** | Markdown | `operation/performance-validation/nfr-validation-matrix.md` |
| **Questions** | Markdown | `operation/performance-validation/performance-validation-questions.md` |
| **Raw k6 Results** | JSON | `s3://andes-retail-loadtests/YYYY-MM-DD/results.json` |
| **Grafana Snapshots** | PNG/PDF | `s3://andes-retail-loadtests/YYYY-MM-DD/snapshots/` |