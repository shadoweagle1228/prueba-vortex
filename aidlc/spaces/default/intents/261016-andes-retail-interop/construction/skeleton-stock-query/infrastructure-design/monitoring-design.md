## Sources

- [observability-requirements.md] Requerimientos de observabilidad y métricas.
- [observability-design.md] Diseño de métricas Prometheus y MDC.
- [NFR-04, NFR-10] SLAs y Circuit Breaker.

---

# Diseño de Monitoreo y Telemetría — `skeleton-stock-query`

Este documento describe la arquitectura de monitoreo en formato tabular para el servicio `stock-price-adapter` y la infraestructura base.

---

## 1. Catálogo de Métricas y Fuentes de Scraping

| Métrica | Fuente / Componente | Tipo | Intervalo de Scraping | Umbral Normal | Umbral Crítico |
|---|---|---|---|---|---|
| `stock_query_duration_seconds` | Pod Spring Boot (`/actuator/prometheus`) | Histogram | 15 segundos | p95 $\le 50\text{ms}$ (cache) | p95 > 300ms [ALM-LAT-P95] |
| `cache_requests_total` | Pod Spring Boot | Counter | 15 segundos | Hit ratio $\ge 90\%$ | Hit ratio < 75% |
| `erp_circuit_breaker_state` | Resilience4j Micrometer | Gauge | 5 segundos | 0 (`CLOSED`) | 1 (`OPEN`) [ALM-CB-OPEN] |
| `erp_bulkhead_available_concurrent_calls` | Resilience4j Micrometer | Gauge | 5 segundos | > 5 hilos libres | 0 hilos (pool saturado) |
| `container_cpu_usage_seconds_total` | Kubernetes cAdvisor | Counter | 15 segundos | < 70% por pod | > 85% por pod |
| `container_memory_working_set_bytes` | Kubernetes cAdvisor | Gauge | 15 segundos | < 600 MiB | > 850 MiB (Riesgo OOM) |
| `redis_connected_clients` | AWS CloudWatch (ElastiCache) | Gauge | 60 segundos | < 50 clientes | > 100 clientes |
| `redis_cpu_utilization` | AWS CloudWatch (ElastiCache) | Gauge | 60 segundos | < 40% CPU | > 75% CPU [ALM-REDIS-SAT] |

---

## 2. Definición de Alarmas y Notificaciones

| ID Alarma | Expresión PromQL / CloudWatch | Ventana de Evaluación | Severidad | Destino / Acción |
|---|---|---|---|---|
| **ALM-CB-OPEN** | `erp_circuit_breaker_state{instance=~".*"} == 1` | 1 minuto | CRÍTICA | PagerDuty / Canal Slack `#ops-critical` |
| **ALM-LAT-P95** | `histogram_quantile(0.95, sum(rate(stock_query_duration_seconds_bucket[5m])) by (le)) > 0.3` | 5 minutos | ALTA | Notificación Slack `#ops-alerts` |
| **ALM-5XX-RATE** | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[3m])) / sum(rate(http_server_requests_seconds_count[3m])) > 0.01` | 3 minutos | CRÍTICA | PagerDuty / Alerta automática |
| **ALM-HPA-MAX** | `kube_horizontalpodautoscaler_status_current_replicas == 10` | 10 minutos | MEDIA | Notificación de saturación de capacidad |

---

## 3. Estructura del Tablero Operacional (Grafana Dashboard)

| Fila del Tablero | Paneles Incluidos | Visualización |
|---|---|---|
| **Fila 1: KPIs Principales** | Throughput Total (req/s), Latencia p95 (ms), Tasa de Error 5xx (%), Cache Hit Ratio (%) | Paneles Stat / Single Value |
| **Fila 2: Rendimiento de Stock** | Latencia p50, p95 y p99 desglosada por `source` (cache vs erp vs stale) | Time Series Chart |
| **Fila 3: Salud del ERP Legacy** | Estado del Circuit Breaker (Closed/Open), Tasa de Fallo SAP (%), Hilos en Bulkhead | State Timeline & Gauge Chart |
| **Fila 4: Recursos de EKS & Redis** | CPU/Memoria por pod, Réplicas HPA activas, Uso de memoria y CPU en Redis | Multi-line Graph |

---

## Assumptions & Open Questions

- [assumption] Prometheus y Grafana son desplegados mediante el operador `kube-prometheus-stack` en el namespace `monitoring` de EKS.
