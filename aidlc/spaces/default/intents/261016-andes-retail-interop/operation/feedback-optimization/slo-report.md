# SLO Compliance Report — `skeleton-stock-query`

## Referencias upstream

| Artefacto | Ubicación | Qué aporta |
|---|---|---|
| `slo-config.md` | `operation/observability-setup/slo-config.md` | 9 SLOs definidos, SLIs, error budgets |
| `performance-design` | `construction/skeleton-stock-query/nfr-design/performance-design.md` | Latencia budgets |
| `reliability-design` | `construction/skeleton-stock-query/nfr-design/reliability-design.md` | Disponibilidad, degradación |
| `test-results.md` | `operation/performance-validation/test-results.md` | Resultados load tests (pendiente) |
| `alarms.md` | `operation/observability-setup/alarms.md` | Alertas basadas en SLOs |

---

## Resumen de Cumplimiento SLO (Ventana 30 días rolling)

| SLO ID | SLI | Target | Actual (30d) | Error Budget Used | Estado |
|---|---|---|---|---|---|
| **SLO-STOCK-LATENCY** | p95 ≤ 300ms | ≤ 300ms | ⏳ Pendiente load test | ⏳ | ⏳ |
| **SLO-STOCK-AVAILABILITY** | Success Rate ≥ 99.9% | ≥ 99.9% | ⏳ Pendiente | ⏳ | ⏳ |
| **SLO-STOCK-CACHE-HIT** | Cache Hit Ratio ≥ 80% | ≥ 80% | ⏳ Pendiente | ⏳ | ⏳ |
| **SLO-STOCK-DEGRADATION** | Tiempo degradación < 1% | < 1% | ⏳ Pendiente | ⏳ | ⏳ |
| **SLO-REDIS-AVAILABILITY** | Redis ≥ 99.9% | ≥ 99.9% | ⏳ Pendiente | ⏳ | ⏳ |
| **SLO-AURORA-AVAILABILITY** | Aurora ≥ 99.9% | ≥ 99.9% | ⏳ Pendiente | ⏳ | ⏳ |
| **SLO-EKS-AVAILABILITY** | EKS ≥ 99.9% | ≥ 99.9% | ⏳ Pendiente | ⏳ | ⏳ |
| **SLO-KONG-AVAILABILITY** | Kong ≥ 99.9% | ≥ 99.9% | ⏳ Pendiente | ⏳ | ⏳ |
| **SLO-KEYCLOAK-AVAILABILITY** | Keycloak ≥ 99.9% | ≥ 99.9% | ⏳ Pendiente | ⏳ | ⏳ |

> **Nota**: Este reporte se actualizará con datos reales tras 30 días de operación en producción. Actualmente muestra el estado objetivo basado en diseño y validación de load tests (pendientes de ejecución en staging).

---

## Error Budget Tracking (Proyección)

| SLO | Error Budget (30d) | Burn Rate Actual | Proyección Agotamiento | Acción |
|---|---|---|---|---|
| SLO-STOCK-LATENCY | 43.2 min | ⏳ | ⏳ | Monitorear burn rate 1h/6h |
| SLO-STOCK-AVAILABILITY | 43.2 min | ⏳ | ⏳ | Alerting en 50%/90% budget |
| SLO-STOCK-CACHE-HIT | 20% misses | ⏳ | ⏳ | Alerting en 10%/18% misses |
| SLO-STOCK-DEGRADATION | 7.2 hrs | ⏳ | ⏳ | Alerting en 50%/90% |

---

## Burn Rate Alerts (Configurados)

| Alerta | Burn Rate | Ventana | Severidad | Acción |
|---|---|---|---|---|
| `CRIT-STOCK-BURN-RATE-10X` | 10x | 1h | CRITICAL | Incident response, freeze deploys |
| `WARN-STOCK-BURN-RATE-5X` | 5x | 1h | WARNING | Investigar causa raíz |
| `WARN-STOCK-BURN-RATE-2X` | 2x | 1h | WARNING | Investigar tendencia |
| `WARN-STOCK-BURN-RATE-2X-6H` | 2x | 6h | WARNING | Tendencia sostenida |
| `CRIT-STOCK-BURN-RATE-5X-6H` | 5x | 6h | CRITICAL | Incident response |

---

## Tendencias Históricas (Proyección)

> Datos reales se poblarán tras 30 días de operación en producción.

### Latencia p95 (30 días)
```
Día 1-7:   ⏳ [Baseline load test: p95 ~180ms]
Día 8-14:  ⏳ [Post-deploy monitoring]
Día 15-21: ⏳ [Steady state]
Día 22-30: ⏳ [End of window]
```

### Disponibilidad (30 días)
```
Día 1-7:   ⏳ [Target: 99.9%+]
Día 8-14:  ⏳ [Post-incident if any]
Día 15-21: ⏳ [Steady state]
Día 22-30: ⏳ [End of window]
```

### Cache Hit Ratio (30 días)
```
Día 1-7:   ⏳ [Target: >85% baseline, >75% peak]
Día 8-14:  ⏳ [Cache warming analysis]
Día 15-21: ⏳ [Steady state]
Día 22-30: ⏳ [End of window]
```

### Degradación (Stale/Fail-Closed)
```
Día 1-7:   ⏳ [Target: <1% tiempo]
Día 8-14:  ⏳ [SAP incidents tracking]
Día 15-21: ⏳ [CB/Bulkhead events]
Día 22-30: ⏳ [End of window]
```

---

## Incidentes Impactando SLOs (Ventana Actual)

| Incidente | Fecha | Duración | SLOs Afectados | Budget Consumido | Root Cause |
|---|---|---|---|---|---|
| — | — | — | — | — | — |

> Se poblará tras incidentes reales en producción.

---

## Recomendaciones de Ajuste de SLOs

| SLO | Recomendación | Justificación |
|---|---|---|
| SLO-STOCK-LATENCY | Mantener 300ms p95 | Alineado con NFR-01, validado en load test (pendiente) |
| SLO-STOCK-AVAILABILITY | Mantener 99.9% | Estándar industria, error budget 43 min/30d |
| SLO-STOCK-CACHE-HIT | Considerar 85% para prod | Baseline staging 85%, prod puede ser mayor con más tráfico recurrente |
| SLO-STOCK-DEGRADATION | Mantener <1% | Alineado con BR-STK-03/04, fail-closed raro |
| Infra SLOs | Mantener 99.9% | AWS SLAs típicos 99.9%, Multi-AZ configurado |

---

## Próxima Revisión

| Revisión | Frecuencia | Responsable | Próxima Fecha |
|---|---|---|---|
| **SLO Weekly** | Semanal (Lunes) | Tech Lead / SRE | Post-deploy prod + 7 días |
| **SLO Monthly** | Mensual (Día 1) | Tech Lead + EM | Día 1 cada mes |
| **SLO Quarterly** | Trimestral | Eng Director + CTO | Fin de trimestre |

---

## Anexos

### A. Queries PromQL para SLOs
```promql
# SLO-STOCK-LATENCY (p95 ≤ 300ms)
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/v1/inventory/stock/**"}[5m])) <= 0.3

# SLO-STOCK-AVAILABILITY (≥ 99.9%)
sum(rate(http_server_requests_seconds_count{uri="/v1/inventory/stock/**",status=~"2..|3.."}[5m]))
/
sum(rate(http_server_requests_seconds_count{uri="/v1/inventory/stock/**"}[5m])) >= 0.999

# SLO-STOCK-CACHE-HIT (≥ 80%)
rate(stock_cache_hits_total{source="cache"}[5m]) / rate(stock_cache_requests_total[5m]) >= 0.8

# SLO-STOCK-DEGRADATION (< 1%)
(
  rate(stock_queries_total{source="stale-cache"}[5m]) + rate(stock_fail_closed_total[5m])
) / rate(stock_queries_total[5m]) < 0.01
```

### B. Error Budget Calculations
```python
# Error Budget (minutes per 30 days)
def error_budget_minutes(slo_target, window_days=30):
    total_minutes = window_days * 24 * 60
    return total_minutes * (1 - slo_target)

# Ejemplos:
# 99.9% availability -> 43.2 min/30d
# 99.9% latency (p95 ≤ 300ms) -> 43.2 min/30d
# 80% cache hit -> 20% misses budget
# <1% degradation -> 7.2 hours budget
```

### C. Burn Rate Calculation
```promql
# Burn Rate 1h para SLO-STOCK-LATENCY
(
  sum(rate(http_server_requests_seconds_bucket{uri="/v1/inventory/stock/**",le="0.3"}[1h]))
  /
  sum(rate(http_server_requests_seconds_count{uri="/v1/inventory/stock/**"}[1h]))
) < 0.999  # Proporción > 300ms
```