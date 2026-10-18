# Load Test Results — `skeleton-stock-query`

## Resumen de Ejecución

| Campo | Valor |
|---|---|
| **Fecha ejecución** | Pendiente (requiere entorno staging desplegado) |
| **Entorno** | Staging (pendiente `cdk deploy`) |
| **Herramienta** | k6 v0.50+ |
| **Versión App** | `0.1.0` (tag `v0.1.0` pendiente) |
| **Commit** | `HEAD` (working tree) |
| **Ejecutor** | Pipeline CD / Manual |

> **Nota**: Este documento define el formato de resultados. La ejecución real requiere entorno staging desplegado (`cdk deploy --all --profile andes-staging` + `kubectl apply -k deploy/`).

---

## Resultados por Caso de Prueba

### TC-01: Baseline Steady State (200 RPS, 30 min)

| Métrica | Target | Resultado | PASS/FAIL |
|---|---|---|---|
| **Latencia p50** | ≤ 100 ms | ⏳ Pendiente | ⏳ |
| **Latencia p95** | ≤ 300 ms | ⏳ Pendiente | ⏳ |
| **Latencia p99** | ≤ 500 ms | ⏳ Pendiente | ⏳ |
| **Throughput** | 200 RPS sostenido | ⏳ Pendiente | ⏳ |
| **Error Rate (5xx)** | < 0.1% | ⏳ Pendiente | ⏳ |
| **Cache Hit Ratio** | ≥ 85% | ⏳ Pendiente | ⏳ |
| **Circuit Breaker State** | CLOSED (0) | ⏳ Pendiente | ⏳ |
| **Bulkhead Available** | > 15 | ⏳ Pendiente | ⏳ |

**Análisis**: —

---

### TC-02: Peak Hour (Rampa 200→800 RPS, 2h hold)

| Métrica | Target | Resultado | PASS/FAIL |
|---|---|---|---|
| **Latencia p95 (rampa)** | ≤ 300 ms | ⏳ Pendiente | ⏳ |
| **Latencia p95 (hold)** | ≤ 300 ms | ⏳ Pendiente | ⏳ |
| **HPA Scale-out Time** | < 3 min (2→6 replicas) | ⏳ Pendiente | ⏳ |
| **Error Rate (5xx)** | < 0.1% | ⏳ Pendiente | ⏳ |
| **Cache Hit Ratio (hold)** | ≥ 75% | ⏳ Pendiente | ⏳ |
| **Circuit Breaker** | CLOSED (0) | ⏳ Pendiente | ⏳ |
| **Bulkhead Available** | > 10 | ⏳ Pendiente | ⏳ |
| **HPA Scale-in Time** | < 10 min (6→2 replicas) | ⏳ Pendiente | ⏳ |

**Análisis**: —

---

### TC-03: Burst Stress (1500 RPS, 5 min)

| Métrica | Target | Resultado | PASS/FAIL |
|---|---|---|---|
| **Breaking Point RPS** | Identificado | ⏳ Pendiente | ⏳ |
| **Max Throughput Sostenido** | RPS antes de degradación | ⏳ Pendiente | ⏳ |
| **Circuit Breaker** | OPEN si SAP saturado | ⏳ Pendiente | ⏳ |
| **Bulkhead Rejects** | Contados, < 5% total | ⏳ Pendiente | ⏳ |
| **Error Rate (5xx)** | < 5% (esperado bajo stress) | ⏳ Pendiente | ⏳ |
| **Latencia p99** | < 2000 ms | ⏳ Pendiente | ⏳ |
| **App Stability** | Sin crashes, OOM, restarts | ⏳ Pendiente | ⏳ |

**Análisis**: —

---

### TC-04: Soak Test (300 RPS, 4 horas)

| Métrica | Target | Resultado | PASS/FAIL |
|---|---|---|---|
| **Memory RSS Drift** | < 10% (4h) | ⏳ Pendiente | ⏳ |
| **GC Frequency** | Estable, sin picos > 2x baseline | ⏳ Pendiente | ⏳ |
| **Connection Pool Leaks** | 0 leaks (pool estable) | ⏳ Pendiente | ⏳ |
| **Latencia p95 Drift** | < 10% (inicio vs fin) | ⏳ Pendiente | ⏳ |
| **CPU Utilization** | Estable, < 80% | ⏳ Pendiente | ⏳ |
| **GC Pause Time** | p99 < 100ms | ⏳ Pendiente | ⏳ |

**Análisis**: —

---

### TC-05: Cache Miss Storm (500 RPS, SKUs únicos cold)

| Métrica | Target | Resultado | PASS/FAIL |
|---|---|---|---|
| **Latencia p95 (miss)** | ≤ 500 ms | ⏳ Pendiente | ⏳ |
| **Cache Hit Ratio** | ~0% (todo miss) | ⏳ Pendiente | ⏳ |
| **Circuit Breaker** | CLOSED (0) | ⏳ Pendiente | ⏳ |
| **Bulkhead Available** | > 10 | ⏳ Pendiente | ⏳ |
| **SAP Latency p95** | ≤ 250 ms | ⏳ Pendiente | ⏳ |
| **Fail-Closed Rate** | 0% (SAP sano) | ⏳ Pendiente | ⏳ |

**Análisis**: —

---

### TC-06: SAP Degradation (Mock Latency 500ms → 2s)

| Métrica | Target | Resultado | PASS/FAIL |
|---|---|---|---|
| **CB Opens** | En threshold (slow call rate > 50%) | ⏳ Pendiente | ⏳ |
| **Half-Open Attempts** | Cada 60s | ⏳ Pendiente | ⏳ |
| **Stale Served** | Sí, `X-Stock-Source: stale-cache` | ⏳ Pendiente | ⏳ |
| **Fail-Closed** | 503 + `Retry-After: 5` si no stale | ⏳ Pendiente | ⏳ |
| **Latencia p95 (CB Open)** | ≤ 500 ms (servido desde stale) | ⏳ Pendiente | ⏳ |
| **Recovery** | CB CLOSED tras SAP recovery | ⏳ Pendiente | ⏳ |

**Análisis**: —

---

### TC-07: Redis Failover (Trigger Manual)

| Métrica | Target | Resultado | PASS/FAIL |
|---|---|---|---|
| **Failover Time** | < 60 segundos | ⏳ Pendiente | ⏳ |
| **Stale Served** | Durante failover | ⏳ Pendiente | ⏳ |
| **App Errors** | 0 (degradación transparente) | ⏳ Pendiente | ⏳ |
| **Cache Repopulated** | < 5 min (hit ratio > 80%) | ⏳ Pendiente | ⏳ |
| **App Latency p95** | < 500 ms durante failover | ⏳ Pendiente | ⏳ |

**Análisis**: —

---

### TC-08: Rate Limiting (101 req/s mismo consumer)

| Métrica | Target | Resultado | PASS/FAIL |
|---|---|---|---|
| **Requests 1-100** | 200 OK | ⏳ Pendiente | ⏳ |
| **Request 101** | 429 Too Many Requests | ⏳ Pendiente | ⏳ |
| **Headers Rate Limit** | `X-RateLimit-Limit: 100`, `Retry-After` | ⏳ Pendiente | ⏳ |
| **Otros Consumers** | No afectados (200 OK) | ⏳ Pendiente | ⏳ |
| **Kong Logs** | Rate limit logged | ⏳ Pendiente | ⏳ |

**Análisis**: —

---

## Resumen Global

| Test Case | Status | Blocker |
|---|---|---|
| TC-01 Baseline | ⏳ Pendiente | Entorno staging no desplegado |
| TC-02 Peak Hour | ⏳ Pendiente | Entorno staging no desplegado |
| TC-03 Burst | ⏳ Pendiente | Entorno staging no desplegado |
| TC-04 Soak | ⏳ Pendiente | Entorno staging no desplegado |
| TC-05 Cache Miss | ⏳ Pendiente | Entorno staging no desplegado |
| TC-06 SAP Degradation | ⏳ Pendiente | Entorno staging no desplegado |
| TC-07 Redis Failover | ⏳ Pendiente | Entorno staging no desplegado |
| TC-08 Rate Limit | ⏳ Pendiente | Entorno staging no desplegado |

---

## Artefactos Generados (Post-Ejecución)

| Archivo | Ubicación (S3) | Descripción |
|---|---|---|
| `results.json` | `s3://andes-retail-loadtests/YYYY-MM-DD/results.json` | k6 raw output completo |
| `summary.json` | `s3://andes-retail-loadtests/YYYY-MM-DD/summary.json` | k6 summary export |
| `test-results.md` | `operation/performance-validation/test-results.md` | Este documento (actualizado) |
| `nfr-validation-matrix.md` | `operation/performance-validation/nfr-validation-matrix.md` | Matriz validación NFR |
| Grafana Snapshots | `s3://andes-retail-loadtests/YYYY-MM-DD/snapshots/` | PNG dashboards Golden Signals, Infra, Business |
| k6 Raw Logs | `s3://andes-retail-loadtests/YYYY-MM-DD/logs/` | k6 stdout/stderr |

---

## Próximos Pasos

1. **Desplegar Staging**: `cd infra && cdk deploy --all --profile andes-staging`
2. **Deploy K8s**: `kubectl apply -k deploy/k8s/ && kubectl apply -k deploy/kong/ && kubectl apply -k deploy/k8s/monitoring/`
3. **Configurar k6**: Variables `BASE_URL`, `TOKEN` en runners
4. **Ejecutar Suite**: `k6 run load-test.js --out json=results.json`
5. **Actualizar este doc**: Rellenar tabla resultados con datos reales
6. **Generar Matriz NFR**: `nfr-validation-matrix.md`
7. **Decisión Go/No-Go**: Para promoción a prod