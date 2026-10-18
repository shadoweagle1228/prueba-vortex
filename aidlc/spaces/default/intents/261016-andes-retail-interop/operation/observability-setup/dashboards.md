# Dashboards — `skeleton-stock-query`

## Referencias upstream

| Artefacto | Ubicación | Qué aporta |
|---|---|---|
| `performance-design` | `construction/skeleton-stock-query/nfr-design/performance-design.md` | Latencia p95 ≤ 300ms, cache hit < 15ms, throughput |
| `reliability-design` | `construction/skeleton-stock-query/nfr-design/reliability-design.md` | CB/Bulkhead params, degradación controlada, fail-closed |
| `monitoring-design` | `construction/skeleton-stock-query/nfr-design/monitoring-design.md` | Golden signals, SLOs, alertas |
| `infrastructure-specification` | `construction/skeleton-stock-query/infrastructure-design/infrastructure-specification.md` | EKS, Redis, Aurora, Kong, Keycloak specs |
| `deployment-execution` | `operation/deployment-execution/deployment-log.md` | Procedimiento deploy, smoke tests, health checks |

---

## Dashboard 1: Golden Signals (Stock Adapter)

**Nombre**: `AndesRetail/StockAdapter/GoldenSignals`
**Fuente**: Prometheus (ServiceMonitor `stock-price-adapter`)
**Refresh**: 30s
**Time range default**: 1h

### Paneles

| Panel | Métrica (PromQL) | Tipo | Umbrales | Descripción |
|---|---|---|---|---|
| **Latencia p50/p95/p99** | `histogram_quantile(0.5, rate(http_server_requests_seconds_bucket{uri="/v1/inventory/stock/**"}[5m]))`<br>`histogram_quantile(0.95, ...)`<br>`histogram_quantile(0.99, ...)` | Time series | p95 > 300ms (warn), > 500ms (crit) | NFR-01: p95 ≤ 300ms |
| **Tráfico (RPS)** | `rate(http_server_requests_seconds_count{uri="/v1/inventory/stock/**"}[5m])` | Time series | — | Requests por segundo |
| **Error Rate (5xx)** | `rate(http_server_requests_seconds_count{uri="/v1/inventory/stock/**",status=~"5.."}[5m])` | Time series | > 1% (warn), > 5% (crit) | Disponibilidad |
| **Error Rate (4xx)** | `rate(http_server_requests_seconds_count{uri="/v1/inventory/stock/**",status=~"4.."}[5m])` | Time series | > 10% (warn) | Client errors |
| **Cache Hit Ratio** | `rate(stock_cache_hits_total[5m]) / rate(stock_cache_requests_total[5m])` | Gauge | < 0.8 (warn), < 0.5 (crit) | BR-STK-01 |
| **Circuit Breaker State** | `resilience4j_circuitbreaker_state{name="sapStockCircuitBreaker"}` | Stat/Time series | 0=Closed, 1=Open, 2=Half-Open | NFR-10, BR-STK-02 |
| **Bulkhead Available** | `resilience4j_bulkhead_available_concurrent_calls{name="sapStockBulkhead"}` | Gauge | < 5 (warn), = 0 (crit) | NFR-12, BR-STK-05 |
| **SAP Latency** | `histogram_quantile(0.95, rate(sap_client_latency_seconds_bucket[5m]))` | Time series | > 250ms (warn) | BR-STK-02 |

---

## Dashboard 2: Infraestructura AWS

**Nombre**: `AndesRetail/Infrastructure/Overview`
**Fuente**: CloudWatch Metrics + Prometheus (node-exporter, kube-state-metrics)

### Paneles

| Panel | Métrica | Tipo | Umbrales | Descripción |
|---|---|---|---|---|
| **EKS Cluster Status** | `cluster_status` (Custom) | Stat | ACTIVE=OK | `aws eks describe-cluster` |
| **Node Count** | `kube_node_status_condition{condition="Ready",status="true"}` | Gauge | 2-6 | HPA target |
| **Node CPU/Memory** | `node_cpu_usage_seconds_total`, `node_memory_MemAvailable_bytes` | Time series | CPU > 80% (warn), Mem < 20% (warn) | Capacity |
| **Pod Restarts** | `kube_pod_container_status_restarts_total` | Counter | > 5/15min (warn) | Estabilidad |
| **Redis CPU** | `aws_elasticache_CPUUtilization` | Time series | > 80% (warn) | NFR-01 |
| **Redis Memory** | `aws_elasticache_FreeableMemory` | Time series | < 20% free (warn) | BR-STK-01 |
| **Aurora CPU** | `aws_rds_CPUUtilization` | Time series | > 80% (warn) | NFR-04 |
| **Aurora Connections** | `aws_rds_DatabaseConnections` | Gauge | > 80% max (warn) | FR-11 |
| **ALB/Kong Latency** | `aws_alb_TargetResponseTime` | Time series | p95 > 500ms (warn) | Edge latency |
| **ALB 5xx** | `aws_alb_HTTPCode_Target_5XX_Count` | Counter | > 0 (warn) | Edge errors |

---

## Dashboard 3: Seguridad y Compliance

**Nombre**: `AndesRetail/Security/Compliance`
**Fuente**: CloudWatch, CloudTrail, GuardDuty, Config

### Paneles

| Panel | Métrica/Check | Tipo | Descripción |
|---|---|---|---|
| **TLS Version** | `ssl_version_distribution` (nmap/cloudwatch) | Pie chart | Solo TLS 1.3 = OK |
| **Cert Expiry** | `aws_acm_certificate_expiry_days` | Gauge | < 30 días = warn |
| **KMS Rotation** | `aws_kms_key_rotation_status` | Stat | Enabled = OK |
| **Secrets Rotation** | `secretsmanager_secret_age_days` | Table | > 90 días = warn |
| **Config Compliance** | `aws_config_compliance_summary` | Bar | Non-compliant = 0 target |
| **GuardDuty Findings** | `aws_guardduty_findings_count` | Counter | High/Critical = 0 target |
| **Rate Limit Violations** | `kong_rate_limit_exceeded_total` | Counter | 429 responses tracking |

---

## Dashboard 4: Business / Stock Operations

**Nombre**: `AndesRetail/Business/StockOperations`
**Fuente**: Prometheus (custom metrics) + Logs

### Paneles

| Panel | Métrica | Tipo | Descripción |
|---|---|---|---|
| **Stock Queries by Source** | `rate(stock_queries_total{source="cache"}[5m])`, `rate(stock_queries_total{source="erp"}[5m])`, `rate(stock_queries_total{source="stale-cache"}[5m])` | Stacked area | Distribución cache/erp/stale |
| **Stock Queries by Channel** | `rate(stock_queries_total{channel="canal-ecommerce"}[5m])`, ... | Stacked area | Uso por canal (Kong consumer) |
| **SKU Top Queried** | `topk(10, rate(stock_queries_by_sku_total[5m]))` | Table | SKUs más consultados |
| **Fail-closed Events** | `rate(stock_fail_closed_total[5m])` | Counter | Eventos 503 sin stale |
| **Degradación Stale** | `rate(stock_stale_served_total[5m])` | Counter | Veces que se sirvió stale-cache |

---

## Configuración técnica (CloudWatch Dashboard JSON)

```json
{
  "widgets": [
    {
      "type": "metric",
      "x": 0, "y": 0, "width": 12, "height": 6,
      "properties": {
        "metrics": [
          ["AWS/ApplicationELB", "TargetResponseTime", "TargetGroup", "stock-price-adapter-tg", { "stat": "p95" }],
          ["AWS/ApplicationELB", "TargetResponseTime", "TargetGroup", "stock-price-adapter-tg", { "stat": "p50" }]
        ],
        "period": 300,
        "stat": "Average",
        "region": "us-east-1",
        "title": "ALB Latency (p50/p95)",
        "yAxis": { "left": { "min": 0 } }
      }
    },
    {
      "type": "metric",
      "x": 12, "y": 0, "width": 12, "height": 6,
      "properties": {
        "metrics": [
          ["Prometheus", "resilience4j_circuitbreaker_state", "name", "sapStockCircuitBreaker"],
          ["Prometheus", "resilience4j_bulkhead_available_concurrent_calls", "name", "sapStockBulkhead"]
        ],
        "period": 60,
        "stat": "Average",
        "region": "us-east-1",
        "title": "Resilience4j State"
      }
    }
  ]
}
```

---

## Variables de dashboard (template variables)

| Variable | Tipo | Query | Uso |
|---|---|---|---|
| `$env` | Query | `label_values(prometheus, environment)` | Filtrar staging/prod |
| `$instance` | Query | `label_values(prometheus, instance)` | Filtrar por pod |
| `$channel` | Query | `label_values(stock_queries_total, channel)` | Filtrar por canal |
| `$source` | Query | `label_values(stock_queries_total, source)` | Filtrar cache/erp/stale |

---

## Retención y permisos

| Aspecto | Configuración |
|---|---|
| **Retención métricas** | 15 días (Prometheus), 13 meses (CloudWatch) |
| **Permisos lectura** | Equipo SRE, Tech Leads, On-call |
| **Permisos edición** | SRE Lead, Platform Team |
| **Alertas vinculadas** | Ver `alarms.md` |