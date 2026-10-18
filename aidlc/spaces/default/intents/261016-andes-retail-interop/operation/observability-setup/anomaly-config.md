# Anomaly Detection Configuration — `skeleton-stock-query`

## Referencias upstream

| Artefacto | Ubicación | Qué aporta |
|---|---|---|
| `monitoring-design` | `construction/skeleton-stock-query/nfr-design/monitoring-design.md` | SLOs, golden signals, anomaly detection strategy |
| `performance-design` | `construction/skeleton-stock-query/nfr-design/performance-design.md` | Baselines de latencia |
| `reliability-design` | `construction/skeleton-stock-query/nfr-design/reliability-design.md` | Degradación controlada, CB/Bulkhead |
| `dashboards.md` | `operation/observability-setup/dashboards.md` | Métricas base |
| `alarms.md` | `operation/observability-setup/alarms.md` | Alarmas estáticas (umbrales fijos) |

---

## Estrategia de Detección de Anomalías

### Objetivo
Complementar alarmas estáticas (umbrales fijos) con detección basada en comportamiento histórico para:
- Detectar degradaciones graduales (drift)
- Identificar patrones estacionales/anómalos
- Reducir falsos positivos/negativos
- Alertar proactivamente antes de cruzar umbrales críticos

### Enfoque Híbrido

| Tipo | Método | Uso |
|---|---|---|
| **Umbrales estáticos** | Alarmas CloudWatch/Prometheus (alarms.md) | Críticos conocidos, SLO hard limits |
| **Detección estadística** | CloudWatch Anomaly Detection / Prometheus rule groups | Métricas con estacionalidad (latencia, tráfico) |
| **ML-based** | AWS Lookout for Metrics / custom ML | Detección multi-dimensional, correlación |

---

## CloudWatch Anomaly Detection (Métricas AWS)

### Métricas con Anomaly Detection habilitado

| Métrica | Namespace | Dimensiones | Banda esperada | Acción al detectar anomalía |
|---|---|---|---|---|
| `CPUUtilization` (EKS Nodes) | `AWS/EC2` / `ContainerInsights` | `AutoScalingGroupName` | Diurna/nocturna | `WARN-ANOMALY-EKS-CPU` |
| `MemoryUtilization` (EKS Nodes) | `AWS/EC2` / `ContainerInsights` | `AutoScalingGroupName` | Estable | `WARN-ANOMALY-EKS-MEM` |
| `CPUUtilization` (ElastiCache) | `AWS/ElastiCache` | `ReplicationGroupId` | Diurna (picos cache) | `WARN-ANOMALY-REDIS-CPU` |
| `FreeableMemory` (ElastiCache) | `AWS/ElastiCache` | `ReplicationGroupId` | Decreciente gradual | `WARN-ANOMALY-REDIS-MEM` |
| `CPUUtilization` (Aurora) | `AWS/RDS` | `DBClusterIdentifier` | Carga batch nocturno | `WARN-ANOMALY-AURORA-CPU` |
| `DatabaseConnections` (Aurora) | `AWS/RDS` | `DBClusterIdentifier` | Picos login mañana | `WARN-ANOMALY-AURORA-CONN` |
| `RequestCount` (ALB) | `AWS/ApplicationELB` | `TargetGroup` | Patrón diario comercio | `WARN-ANOMALY-ALB-TRAFFIC` |
| `TargetResponseTime` (ALB) | `AWS/ApplicationELB` | `TargetGroup` | Picos hora punta | `WARN-ANOMALY-ALB-LATENCY` |

### Configuración Anomaly Detection (CloudWatch)

```json
{
  "AlarmName": "WARN-ANOMALY-REDIS-CPU",
  "AlarmDescription": "Anomalía en CPU ElastiCache (banda esperada violada)",
  "Metrics": [
    {
      "Id": "ad1",
      "Expression": "ANOMALY_DETECTION_BAND(m1, 2)",
      "Label": "Banda esperada (2σ)"
    },
    {
      "Id": "m1",
      "MetricStat": {
        "Metric": {
          "Namespace": "AWS/ElastiCache",
          "MetricName": "CPUUtilization",
          "Dimensions": [{"Name": "ReplicationGroupId", "Value": "andes-retail-redis"}]
        },
        "Period": 300,
        "Stat": "Average"
      },
      "ReturnData": true
    }
  ],
  "ThresholdMetricId": "ad1",
  "ComparisonOperator": "GreaterThanUpperThreshold",
  "EvaluationPeriods": 3,
  "DatapointsToAlarm": 3,
  "TreatMissingData": "missing"
}
```

---

## Prometheus Rule Groups (Anomalías Aplicación)

### Reglas de Anomalía (PrometheusRule)

```yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: stock-adapter-anomalies
  namespace: andes-retail
spec:
  groups:
  - name: stock-adapter-anomalies
    interval: 60s
    rules:
    # Latencia anómala (drift gradual)
    - alert: WARN-ANOMALY-STOCK-LATENCY-DRIFT
      expr: |
        (
          histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/v1/inventory/stock/**"}[5m]))
          >
          (1.5 * histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/v1/inventory/stock/**"}[1h]))
        )
      for: 15m
      labels:
        severity: warning
        category: anomaly
      annotations:
        summary: "Latencia p95 50% encima de baseline 1h (posible drift)"
        runbook: "https://wiki.andes-retail.com/runbooks/latency-drift"

    # Cache hit ratio drop anómalo
    - alert: WARN-ANOMALY-CACHE-HIT-RATIO-DROP
      expr: |
        (
          rate(stock_cache_hits_total{source="cache"}[5m]) / rate(stock_cache_requests_total[5m])
          <
          0.7 * (rate(stock_cache_hits_total{source="cache"}[1h]) / rate(stock_cache_requests_total[1h]))
        )
      for: 10m
      labels:
        severity: warning
        category: anomaly
      annotations:
        summary: "Cache hit ratio 30% debajo de baseline 1h"

    # Tráfico anómalo (spike o drop)
    - alert: WARN-ANOMALY-STOCK-TRAFFIC-SPIKE
      expr: |
        (
          rate(http_server_requests_seconds_count{uri="/v1/inventory/stock/**"}[5m])
          >
          3 * rate(http_server_requests_seconds_count{uri="/v1/inventory/stock/**"}[1h])
        )
      for: 5m
      labels:
        severity: warning
        category: anomaly
      annotations:
        summary: "Tráfico 3x encima de baseline 1h (posible ataque o bug)"

    - alert: WARN-ANOMALY-STOCK-TRAFFIC-DROP
      expr: |
        (
          rate(http_server_requests_seconds_count{uri="/v1/inventory/stock/**"}[5m])
          <
          0.1 * rate(http_server_requests_seconds_count{uri="/v1/inventory/stock/**"}[1h])
        )
      for: 10m
      labels:
        severity: warning
        category: anomaly
      annotations:
        summary: "Tráfico 90% debajo de baseline 1h (posible outage upstream)"

    # Circuit Breaker state changes anómalos
    - alert: WARN-ANOMALY-CB-FLAPPING
      expr: |
        changes(resilience4j_circuitbreaker_state{name="sapStockCircuitBreaker"}[15m]) > 3
      for: 5m
      labels:
        severity: warning
        category: anomaly
      annotations:
        summary: "Circuit Breaker flapping (>3 cambios en 15min)"

    # Bulkhead utilization anómala
    - alert: WARN-ANOMALY-BULKHEAD-UTILIZATION
      expr: |
        (
          1 - (resilience4j_bulkhead_available_concurrent_calls{name="sapStockBulkhead"} / 20)
          > 0.8
        )
      for: 10m
      labels:
        severity: warning
        category: anomaly
      annotations:
        summary: "Bulkhead >80% utilizado sostenido (baseline <50%)"
```

---

## AWS Lookout for Metrics (ML-based)

### Configuración (Opcional - Fase 2)

| Dataset | Métricas | Frecuencia | Detección |
|---|---|---|---|
| **StockAdapter Golden Signals** | Latencia p50/p95/p99, RPS, Error Rate, Cache Hit, CB State, Bulkhead | 5 min | Multivariate, correlación |
| **Infra AWS** | EKS CPU/Mem, Redis CPU/Mem, Aurora CPU/Conn, ALB Latency/Count | 5 min | Multivariate, correlación cross-stack |

### Configuración (Fase 2)
```json
{
  "AnomalyDetectorName": "AndesRetail-StockAdapter-GoldenSignals",
  "MetricSetList": [
    {
      "MetricSetName": "GoldenSignals",
      "MetricList": [
        {"Name": "latency_p95", "ColumnName": "latency_p95"},
        {"Name": "rps", "ColumnName": "rps"},
        {"Name": "error_rate", "ColumnName": "error_rate"},
        {"Name": "cache_hit_ratio", "ColumnName": "cache_hit_ratio"},
        {"Name": "cb_state", "ColumnName": "cb_state"},
        {"Name": "bulkhead_available", "ColumnName": "bulkhead_available"}
      ],
      "TimestampColumn": "timestamp",
      "DimensionList": ["environment", "instance"]
    }
  ],
  "Frequency": "PT5M",
  "AnomalyDetectorConfig": {
    "AnomalyDetectorFrequency": "PT5M",
    "SensitivityThreshold": 70
  }
}
```

### Alertas Lookout
| Alerta | Condición | Severidad |
|---|---|---|
| `ML-ANOMALY-GOLDEN-SIGNALS` | Score anomalía > 70 | WARNING |
| `ML-ANOMALY-GOLDEN-SIGNALS-CRIT` | Score anomalía > 90 | CRITICAL |

---

## Correlación de Anomalías (Runbook)

### Cuando múltiples anomalías coinciden

| Combinación | Interpretación | Acción |
|---|---|---|
| Latencia ↑ + Cache Hit ↓ + CB Open | Redis caído → App consulta SAP → CB protege | Verificar Redis, failover si necesario |
| Tráfico ↑↑ + Error Rate ↑ + Rate Limit 429 ↑ | Ataque DDoS / bot | Bloquear IPs en WAF/Kong, scale Kong |
| Tráfico ↓↓ + Latencia normal | Outage upstream (Kong/ALB) | Verificar Kong, ALB, DNS |
| CPU Redis ↑ + Memoria ↓ + Cache Hit ↓ | Redis memory pressure | Scale-up Redis, revisar TTLs/keys |
| CPU Aurora ↑ + Conexiones ↑ + Keycloak errors | Keycloak storm | Reiniciar Keycloak pods, revisar pool |

---

## Configuración de Entrenamiento (Baseline)

| Métrica | Período entrenamiento | Estacionalidad esperada | Re-entrenamiento |
|---|---|---|---|
| Latencia p95 | 14 días | Diaria (pico 10-14h, 18-22h) | Semanal |
| RPS | 14 días | Diaria + semanal (fin de semana bajo) | Semanal |
| Cache Hit Ratio | 14 días | Estable (>80%) | Mensual |
| CPU Redis | 14 días | Diaria | Semanal |
| CPU Aurora | 14 días | Diaria + batch nocturno | Semanal |

### Re-entrenamiento automático
- **CloudWatch Anomaly Detection**: Automático (continuo)
- **Prometheus Rules**: Manual (ajustar multiplicadores 1.5x, 3x, 0.7x)
- **Lookout for Metrics**: Automático (diario)

---

## Runbook de Respuesta a Anomalías

| Paso | Acción | Responsable | Tiempo |
|---|---|---|---|
| 1. **Confirmar** | Verificar dashboard, comprobar si es falso positivo | On-call | < 5 min |
| 2. **Clasificar** | Determinar causa raíz (infra/app/upstream) | On-call + SRE | < 15 min |
| 3. **Mitigar** | Aplicar fix inmediato (scale, restart, rollback) | On-call + SRE | < 30 min |
| 4. **Comunicar** | Post en Slack `#andes-alerts`, actualizar status page | On-call | < 5 min |
| 5. **Root Cause** | Análisis post-mortem (5 whys) | SRE + Tech Lead | < 24h |
| 6. **Mejorar** | Ajustar umbrales, añadir tests, mejorar resiliencia | Tech Lead | < 1 sprint |

---

## Métricas de Efectividad

| KPI | Objetivo | Medición |
|---|---|---|
| **Precision (falsos positivos)** | < 20% | Alertas resueltas como "no action needed" / total |
| **Recall (falsos negativos)** | < 5% | Incidentes no alertados / total incidentes |
| **MTTD (Mean Time to Detect)** | < 5 min | Timestamp anomalía → alerta |
| **MTTA (Mean Time to Acknowledge)** | < 5 min | Alerta → on-call ack |
| **MTTR (Mean Time to Resolve)** | < 30 min | Alerta → resuelto |

---

## Costos Estimados

| Componente | Costo mensual (staging) |
|---|---|
| CloudWatch Anomaly Detection | ~$0.50/métrica/mes (~10 métricas = $5) |
| Prometheus Rules | $0 (incluido en Prometheus) |
| Lookout for Metrics (Fase 2) | ~$0.50/métrica/hora × 6 métricas × 730h = ~$220/mes |
| **Total estimado (sin Lookout)** | **~$5-10/mes** |
| **Total con Lookout (Fase 2)** | **~$230/mes** |