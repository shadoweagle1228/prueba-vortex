# Performance Validation Questions — `skeleton-stock-query`

## Preguntas de clarificación (resueltas)

### 1. ¿Patrones de tráfico esperados?
**Respuesta**: Documentados en `load-test-plan.md`:
- **Steady State**: 200 RPS, Zipf (α=1.2), 10K SKUs, 85% cache hit
- **Peak Hour**: 800 RPS (4x), 2h hold, rampa 10m/15m, 75% cache hit
- **Burst**: 1500 RPS (5 min), breaking point
- **Soak**: 300 RPS, 4h (memory leaks, GC pressure)

### 2. ¿Latencia objetivo (percentiles)?
**Respuesta**: 
- **p95 ≤ 300 ms** (NFR-01, SLO-STOCK-LATENCY)
- **p99 ≤ 500 ms** (Performance-design)
- **Cache hit < 15 ms** (BR-STK-01)

### 3. ¿Throughput requerido?
**Respuesta**:
- **Sostenido**: 200 RPS (baseline)
- **Pico**: 800 RPS (2h) — 4x baseline
- **Burst**: 1500 RPS (5 min) — breaking point

### 4. ¿Cuellos de botella probables?
**Respuesta**: Identificados en `scalability-design.md` y `performance-design.md`:
1. **SAP ERP** (latencia 250ms, timeout estricto) — CB/Bulkhead protegen
2. **Redis ElastiCache** (cache hit ratio, memoria, CPU) — Multi-AZ, TTL 60s
3. **HPA Scale-out latency** (3 min target) — Metrics Server, custom metrics
4. **Kong Rate Limiting** (100 req/s) — Plugin config, Redis backend
5. **Aurora Keycloak** (conexiones pool) — Serverless v2 auto-scale

---

## Preguntas abiertas / decisiones pendientes

### 5. ¿Entorno de pruebas representativo?
| Pregunta | Estado | Decisión |
|---|---|---|
| ¿Staging == Prod specs? | **Sí, requerido** | Mismos node types (c7g.medium), Redis (cache.t4g.medium), Aurora Serverless v2, EKS 1.29 |
| ¿Datos realistas? | **Sí** | 10K SKUs en Redis, distribución Zipf, SAP mock con latencia configurable |
| ¿Red misma topología? | **Sí** | Misma VPC, subnets, SGs, ALB/Kong |
| ¿Rate Limiting activo? | **No para load test** | Desactivar o límite alto (10000 req/s) en Kong para tests de carga |
| ¿Datos Keycloak? | Realm `andes-retail` con 3 clients + scopes | Importar `deploy/local/keycloak/andes-retail-realm.json` |

### 6. ¿Herramienta de carga?
| Opción | Pros | Contras | Decisión |
|---|---|---|---|
| **k6** | Go, scripting JS, metrics built-in, cloud/run local, OpenTelemetry | Scripting JS (no TS) | **Seleccionado** |
| Gatling | Scala, DSL rico, reports HTML | JVM, más pesado | Alternativa |
| Locust | Python, distribuido, UI web | Python GIL, menos métricas nativas | No |

**Configuración k6**: 3 runners c6g.xlarge (4 vCPU, 8GB), 400 VUs totales, think time 100ms → ~800 RPS peak.

### 7. ¿Métricas de validación automáticas?
| Métrica | Threshold PASS | Fuente |
|---|---|---|
| `http_req_duration{p95}` | < 300ms | k6 threshold |
| `http_req_failed` | < 0.1% | k6 threshold |
| `stock_errors` (custom) | < 0.1% | k6 custom metric |
| Cache Hit Ratio | ≥ 85% (baseline) / ≥ 75% (peak) | Prometheus query |
| CB State | 0 (CLOSED) | Prometheus query |
| Bulkhead Available | > 10 (peak) / > 15 (baseline) | Prometheus query |

### 8. ¿Ejecución en CI/CD?
| Pregunta | Respuesta |
|---|---|
| ¿Load test en pipeline CI? | **No** — muy lento (4h soak), costo runners. Ejecución manual/gate pre-prod. |
| ¿Smoke performance en CI? | **Sí** — k6 smoke test (1 min, 10 VUs) en PR merge gate. |
| ¿Gate pre-prod? | **Sí** — Load test suite completa antes de promover a prod. |

### 9. ¿Capacidad y Costos?
| Recurso | Spec | Costo Estimado (Staging/mes) |
|---|---|---|
| EKS (3x c7g.medium) | 3x 4 vCPU, 8GB | ~$120 |
| ElastiCache | cache.t4g.medium Multi-AZ | ~$60 |
| Aurora Serverless v2 | 0.5-128 ACU | ~$80-200 (variable) |
| ALB + NLB | 2 AZs | ~$45 |
| CloudWatch (Logs/Metrics) | Logs 30d, Metrics 15d | ~$50 |
| X-Ray Traces | 10% sampling | ~$10 |
| **Total Estimado** | | **~$365-500/mes** |

### 9. ¿Datos de Prueba (Test Data)?
| Dato | Volumen | Generación |
|---|---|---|
| SKUs | 10,000 | Script precarga Redis (Zipf α=1.2) |
| Stock Levels | 10K SKUs × 3 bodegas | `available: 0-1000`, `reserved: 0-available` |
| SAP Mock | Configurable latency (0-5000ms) | WireMock / MockServer en K8s |
| Tokens Keycloak | 3 clients (ecommerce, tienda, operador) | Realm import + client credentials flow |

### 10. ¿Monitoreo Durante Tests?
| Componente | Tool | Métricas Clave |
|---|---|---|
| **App** | Prometheus + Grafana | Golden Signals, CB, Bulkhead, Cache |
| **Infra AWS** | CloudWatch Container Insights | EKS CPU/Mem, Redis CPU/Mem, Aurora CPU/Conn |
| **Kong** | Prometheus / Kong Admin API | Rate limit, latency, upstream health |
| **Keycloak** | Prometheus / JMX | Auth latency, token issuance, connections |
| **Tracing** | X-Ray / Jaeger | End-to-end latency, spans |
| **Logs** | CloudWatch Logs Insights | Errors, correlation-id, CB events |

### 11. ¿Análisis Post-Test?
| Análisis | Herramienta | Output |
|---|---|---|
| Percentiles latencia | k6 summary / Grafana | p50, p90, p95, p99, max |
| Throughput vs Latencia | Grafana scatter | Curva capacidad, breaking point |
| Error Rate breakdown | Logs + Métricas | 5xx por causa (CB, bulkhead, timeout, SAP) |
| Cache Hit Ratio | Prometheus | Time series + heatmap SKU |
| HPA Behavior | k8s events + métricas | Scale events vs RPS timeline |
| CB/Bulkhead Events | Prometheus + Logs | Timeline, correlación con RPS |
| Resource Utilization | CloudWatch | CPU, Mem, Network, Disk por componente |

### 11. ¿Criterios Go/No-Go para Producción?
| Criterio | Go | No-Go (Blocker) |
|---|---|---|
| **TC-01/02 p95 ≤ 300ms** | ✓ | Cualquier FAIL |
| **Error Rate < 0.1%** | ✓ | ≥ 0.1% |
| **HPA Scale-out < 3 min** | ✓ | > 5 min |
| **CB/Bulkhead protegen** | ✓ | CB no abre, bulkhead no rechaza |
| **Redis Failover < 60s** | ✓ | > 2 min |
| **Aurora Failover < 30s** | ✓ | > 60s |
| **Rate Limit correcto** | ✓ | 429 incorrecto |
| **Soak 4h sin leaks** | ✓ | Memory/connection leak |
| **Zero Data Loss** | ✓ | Cualquier pérdida |

---

## Preguntas de Capacidad Futura

### 12. ¿Escalabilidad Horizontal vs Vertical?
| Componente | Actual | Opción Vertical | Opción Horizontal | Decisión |
|---|---|---|---|---|
| **App Pods** | HPA 2-10 | Límites CPU/Mem | Más replicas (max 10) | Horizontal (HPA) |
| **Redis** | cache.t4g.medium | t4g.large / r6g.xlarge | Cluster Mode (sharding) | Vertical → Cluster Mode si > 2000 RPS |
| **Aurora** | Serverless v2 | Max ACU 128 | Aurora Global DB | Serverless v2 auto |
| **Kong** | 2 replicas | Más CPU/Mem | Más replicas + Redis cluster | Horizontal |
| **Keycloak** | 2 replicas | Más CPU/Mem | Más replicas + Infinispan cache | Horizontal |

### 13. ¿Multi-Región / Disaster Recovery?
| Pregunta | Respuesta | Fase |
|---|---|---|
| ¿Active-Passive us-west-2? | Planificado | Fase 2 |
| ¿Aurora Global Database? | Planificado | Fase 2 |
| ¿Redis Global Datastore? | Planificado | Fase 2 |
| ¿Route 53 Failover? | Planificado | Fase 2 |
| ¿RTO Cross-Region? | < 4h (manual) | Fase 2 |
| ¿RPO Cross-Region? | < 1h (snapshot replicate) | Fase 2 |

### 14. ¿Observabilidad en Carga?
| Pregunta | Respuesta |
|---|---|
| ¿Dashboards tiempo real? | Sí, Grafana `AndesRetail/StockAdapter/GoldenSignals` |
| ¿Alertas durante test? | Sí, alarmas CloudWatch/Prometheus activas |
| ¿Tracing distribuido? | Sí, X-Ray + OpenTelemetry (10% sampling) |
| ¿Correlation ID tracking? | Sí, Logs Insights query por correlationId |
| ¿Exemplars Prometheus → Trace? | Sí, habilitado en `application.yml` |

---

## Respuestas registradas (auditoría)

| Pregunta | Respuesta | Fecha | Responsable |
|---|---|---|---|
| Traffic Patterns | Steady 200 RPS, Peak 800 RPS 2h, Burst 1500 RPS, Soak 300 RPS 4h | 2026-10-18 | QA/Performance |
| Latency Targets | p95 ≤ 300ms, p99 ≤ 500ms, Cache hit < 15ms | 2026-10-18 | Performance |
| Throughput | Baseline 200, Peak 800, Burst 1500 RPS | 2026-10-18 | Performance |
| Bottlenecks | SAP (250ms), Redis, HPA latency, Kong RL, Keycloak pool | 2026-10-18 | Architecture |
| Test Environment | Staging = Prod specs, 10K SKUs Zipf, SAP mock, RL disabled | 2026-10-18 | DevOps |
| Load Tool | k6 (3x c6g.xlarge, 400 VUs) | 2026-10-18 | QA |
| Validation Metrics | p95<300ms, error<0.1%, cache≥85%, CB=0, bulkhead>10 | 2026-10-18 | QA |
| CI Integration | Smoke test en PR, Full suite pre-prod gate | 2026-10-18 | DevOps |
| Capacity Costs | ~$500/mes staging | 2026-10-18 | Finance |
| Test Data | 10K SKUs Zipf, SAP mock latency configurable | 2026-10-18 | QA |
| Monitoring | Prometheus/Grafana/X-Ray/Logs Insights activo | 2026-10-18 | Observability |
| Post-Test Analysis | Percentiles, capacity curve, error breakdown, HPA, CB events | 2026-10-18 | QA |
| Go/No-Go Criteria | p95≤300ms, error<0.1%, HPA<3m, CB/BH work, failover<60s, zero leaks | 2026-10-18 | Tech Lead/EM |
| Future Scaling | Horizontal app/Kong/Keycloak, Vertical→Cluster Redis, Serverless Aurora | 2026-10-18 | Architecture |
| DR Multi-region | Fase 2 (Aurora Global, Redis Global, Route53 failover) | 2026-10-18 | Architecture |
| Observability in Load | Golden Signals dashboard, X-Ray, Exemplars, Correlation ID | 2026-10-18 | Observability |