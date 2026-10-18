# SLO Configuration — `skeleton-stock-query`

## Referencias upstream

| Artefacto | Ubicación | Qué aporta |
|---|---|---|
| `monitoring-design` | `construction/skeleton-stock-query/nfr-design/monitoring-design.md` | SLOs, SLIs, error budgets |
| `performance-design` | `construction/skeleton-stock-query/nfr-design/performance-design.md` | Latencia budgets |
| `reliability-design` | `construction/skeleton-stock-query/nfr-design/reliability-design.md` | Disponibilidad, degradación |
| `alarms.md` | `operation/observability-setup/alarms.md` | Alertas basadas en SLOs |

---

## SLOs Definidos

| SLO ID | Servicio | SLI | Objetivo | Ventana | Error Budget | Fuente |
|---|---|---|---|---|---|---|
| `SLO-STOCK-LATENCY` | `stock-price-adapter` | Latencia p95 `GET /v1/inventory/stock/{sku}` | **≤ 300 ms** | 30 días rolling | 43.2 min (0.1%) | NFR-01 |
| `SLO-STOCK-AVAILABILITY` | `stock-price-adapter` | Porcentaje requests exitosos (2xx/3xx) | **≥ 99.9%** | 30 días rolling | 43.2 min | NFR-04 |
| `SLO-STOCK-CACHE-HIT` | `stock-price-adapter` | Cache hit ratio (fresh) | **≥ 80%** | 30 días rolling | 20% misses | BR-STK-01 |
| `SLO-STOCK-DEGRADATION` | `stock-price-adapter` | Tiempo en modo `stale-cache` o `fail-closed` | **< 1%** del tiempo | 30 días rolling | 7.2 horas | BR-STK-03/04 |
| `SLO-REDIS-AVAILABILITY` | `ElastiCache Redis` | Porcentaje tiempo `available` | **≥ 99.9%** | 30 días rolling | 43.2 min | NFR-04 |
| `SLO-AURORA-AVAILABILITY` | `Aurora PostgreSQL` | Porcentaje tiempo `available` | **≥ 99.9%** | 30 días rolling | 43.2 min | NFR-04, FR-11 |
| `SLO-EKS-AVAILABILITY` | `EKS Cluster` | Porcentaje tiempo `ACTIVE` | **≥ 99.9%** | 30 días rolling | 43.2 min | NFR-04 |
| `SLO-KONG-AVAILABILITY` | `Kong Ingress` | Porcentaje tiempo healthy | **≥ 99.9%** | 30 días rolling | 43.2 min | NFR-11 |
| `SLO-KEYCLOAK-AVAILABILITY` | `Keycloak` | Porcentaje tiempo healthy | **≥ 99.9%** | 30 días rolling | 43.2 min | FR-11 |

---

## SLIs (Service Level Indicators)

### SLI-STOCK-LATENCY
- **Tipo**: Latencia (p95)
- **Query PromQL**:
  ```promql
  histogram_quantile(0.95, 
    rate(http_server_requests_seconds_bucket{uri="/v1/inventory/stock/**"}[5m])
  )
  ```
- **Unidad**: Segundos
- **Bueno**: ≤ 0.3s

### SLI-STOCK-AVAILABILITY
- **Tipo**: Disponibilidad (request success rate)
- **Query PromQL**:
  ```promql
  sum(rate(http_server_requests_seconds_count{uri="/v1/inventory/stock/**",status=~"2..|3.."}[5m]))
  /
  sum(rate(http_server_requests_seconds_count{uri="/v1/inventory/stock/**"}[5m]))
  ```
- **Unidad**: Ratio (0-1)
- **Bueno**: ≥ 0.999

### SLI-STOCK-CACHE-HIT
- **Tipo**: Ratio (cache hits / total requests)
- **Query PromQL**:
  ```promql
  rate(stock_cache_hits_total{source="cache"}[5m])
  /
  rate(stock_cache_requests_total[5m])
  ```
- **Unidad**: Ratio (0-1)
- **Bueno**: ≥ 0.8

### SLI-STOCK-DEGRADATION
- **Tipo**: Ratio tiempo en degradación
- **Query PromQL**:
  ```promql
  (
    rate(stock_queries_total{source="stale-cache"}[5m]) 
    + rate(stock_fail_closed_total[5m])
  )
  /
  rate(stock_queries_total[5m])
  ```
- **Unidad**: Ratio (0-1)
- **Bueno**: < 0.01

---

## Error Budget Policy

| SLO | Error Budget (30d) | Alerta Warning (50%) | Alerta Critical (90%) | Acción al agotar |
|---|---|---|---|---|
| `SLO-STOCK-LATENCY` | 43.2 min | 21.6 min p95 > 300ms | 38.9 min | Freeze deployments, root cause analysis |
| `SLO-STOCK-AVAILABILITY` | 43.2 min | 21.6 min downtime | 38.9 min | Incident response, freeze deployments |
| `SLO-STOCK-CACHE-HIT` | 20% misses | 10% misses | 18% misses | Investigar Redis, TTL, invalidaciones |
| `SLO-STOCK-DEGRADATION` | 7.2 hrs | 3.6 hrs | 6.5 hrs | Investigar SAP/Redis, mejorar resiliencia |

---

## Error Budget Burn Rate Alerts

| Burn Rate | Ventana | Alerta | Acción |
|---|---|---|---|
| **1x** (normal) | 1h | Ninguna | Normal |
| **2x** | 1h | `WARN-STOCK-BURN-RATE-2X` | Investigar tendencia |
| **5x** | 1h | `WARN-STOCK-BURN-RATE-5X` | Investigar causa raíz |
| **10x** | 1h | `CRIT-STOCK-BURN-RATE-10X` | Incident response, freeze deploys |
| **2x** | 6h | `WARN-STOCK-BURN-RATE-2X-6H` | Tendencia sostenida |
| **5x** | 6h | `CRIT-STOCK-BURN-RATE-5X-6H` | Incident response |

**Query burn rate (ejemplo latencia)**:
```promql
# Burn rate 1h para SLO-STOCK-LATENCY
(
  sum(rate(http_server_requests_seconds_bucket{uri="/v1/inventory/stock/**",le="0.3"}[1h]))
  /
  sum(rate(http_server_requests_seconds_count{uri="/v1/inventory/stock/**"}[1h]))
) < 0.999  # Inverso: proporción > 300ms
```

---

## SLO Reporting

| Reporte | Frecuencia | Audiencia | Contenido |
|---|---|---|---|
| **SLO Weekly** | Semanal (lunes) | Tech Lead, SRE | Error budget remaining, burn rate, incidentes |
| **SLO Monthly** | Mensual (1ro) | Tech Lead, Engineering Manager, Product | Tendencias, mejoras, capacity planning |
| **SLO Quarterly** | Trimestral | Engineering Director, CTO | Strategic: SLO targets review, investment needs |

---

## Implementación CloudWatch SLO (AWS)

```json
{
  "Name": "SLO-STOCK-LATENCY",
  "ServiceLevelIndicator": {
    "MetricDataQueries": [
      {
        "Id": "good",
        "MetricStat": {
          "Metric": {
            "Namespace": "Prometheus",
            "MetricName": "http_server_requests_seconds_bucket",
            "Dimensions": [
              {"Name": "uri", "Value": "/v1/inventory/stock/**"},
              {"Name": "le", "Value": "0.3"}
            ]
          },
          "Period": 300,
          "Stat": "Sum"
        },
        "ReturnData": true
      },
      {
        "Id": "total",
        "MetricStat": {
          "Metric": {
            "Namespace": "Prometheus",
            "MetricName": "http_server_requests_seconds_count",
            "Dimensions": [
              {"Name": "uri", "Value": "/v1/inventory/stock/**"}
            ]
          },
          "Period": 300,
          "Stat": "Sum"
        },
        "ReturnData": true
      },
      {
        "Id": "sli",
        "Expression": "good / total",
        "Label": "SLI Latency p95 ≤ 300ms",
        "ReturnData": true
      }
    ]
  },
  "Goal": 0.999,
  "Period": 30
}
```

---

## Burn Rate Alerting (CloudWatch)

```yaml
# CRIT-STOCK-BURN-RATE-10X (1h window)
Type: AWS::CloudWatch::Alarm
Properties:
  AlarmName: CRIT-STOCK-BURN-RATE-10X
  AlarmDescription: "Burn rate 10x para latencia - Error budget agotándose en < 1h"
  Metrics:
    - Id: burn_rate
      Expression: "IF(good/total < 0.999, 1, 0)"  # Simplificado
      Label: "Burn Rate"
  Threshold: 0.1  # 10% de requests malos = 10x burn rate (para 99.9% SLO)
  ComparisonOperator: GreaterThanThreshold
  EvaluationPeriods: 1
  Period: 3600
  AlarmActions:
    - arn:aws:sns:us-east-1:111122223333:andes-critical
```

---

## SLO Dashboard (Grafana/CloudWatch)

| Panel | Métrica | Descripción |
|---|---|---|
| **Error Budget Remaining** | `1 - (burn_rate * elapsed / window)` | % budget restante |
| **Burn Rate (1h/6h)** | `burn_rate_1h`, `burn_rate_6h` | Velocidad consumo |
| **SLI Actual vs Target** | `sli_value` vs `goal` | Estado actual |
| **Incidents This Period** | Count de alertas CRIT | Impacto en budget |

---

## Revisión y Ajuste de SLOs

| Evento | Acción |
|---|---|
| **Post-incidente mayor** | Revisar SLOs afectados, ajustar si necesario |
| **Cambio arquitectura** | Re-evaluar SLIs/SLOs (ej. nuevo cache, nueva región) |
| **Trimestral** | Revisión formal con stakeholders |
| **Cambio NFR** | Actualizar SLOs para reflejar nuevos requisitos |