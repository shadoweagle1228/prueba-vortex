# Observability Setup Questions — `skeleton-stock-query`

## Preguntas de clarificación (resueltas)

### 1. ¿Qué golden signals se rastrean?
**Respuesta**: Latencia (p50/p95/p99), Tráfico (RPS), Errores (5xx/4xx rate), Saturación (CPU, Mem, Conexiones, Bulkhead, Cache Hit Ratio). Ver `dashboards.md`.

### 2. ¿Qué SLOs/SLIs están definidos?
**Respuesta**: 9 SLOs definidos en `slo-config.md`:
- Latencia p95 ≤ 300ms (SLO-STOCK-LATENCY)
- Disponibilidad ≥ 99.9% (SLO-STOCK-AVAILABILITY)
- Cache Hit Ratio ≥ 80% (SLO-STOCK-CACHE-HIT)
- Tiempo en degradación < 1% (SLO-STOCK-DEGRADATION)
- Redis/Aurora/EKS/Kong/Keycloak ≥ 99.9% disponibilidad

### 3. ¿Qué layout de dashboards necesita el equipo?
**Respuesta**: 4 dashboards definidos en `dashboards.md`:
1. Golden Signals (Stock Adapter)
2. Infraestructura AWS
3. Seguridad y Compliance
4. Business / Stock Operations

### 4. ¿Qué retención y agregación de logs?
**Respuesta**: 30 días app/Kong, 90 días Keycloak/audit, export a S3 1 año (Glacier). Ver `log-queries.md`.

### 5. ¿Qué trazabilidad distribuida se necesita?
**Respuesta**: OpenTelemetry + AWS X-Ray, 10% sampling success / 100% errors, correlation-id propagado. Ver `tracing-config.md`.

---

## Preguntas abiertas / decisiones pendientes

### 6. ¿SLO targets definitivos para producción?
| SLO | Staging Target | Prod Target (propuesto) | Decisión pendiente |
|---|---|---|---|
| Latencia p95 | ≤ 500ms (más permisivo) | ≤ 300ms | Confirmar prod target |
| Disponibilidad | 99.5% | 99.9% | Confirmar |
| Cache Hit Ratio | ≥ 70% | ≥ 80% | Confirmar |

**Acción**: Validar con Product/Engineering antes de primer deploy prod.

### 7. ¿Error Budget Policy enforcement?
| Pregunta | Opciones | Recomendación |
|---|---|---|
| ¿Freeze deployments al agotar budget? | Sí / No / Solo prod | **Sí, en prod** |
| ¿Quién autoriza override? | Tech Lead / Engineering Manager / CTO | **Engineering Manager** |
| ¿Auto-rollback al cruzar 90% budget? | Sí / No | **No (manual)** |

### 8. ¿Alerting: PagerDuty vs Slack vs Email?
| Severidad | Canal actual | Canal deseado prod |
|---|---|---|
| CRITICAL | Slack `#andes-alerts` | PagerDuty + Slack + SMS |
| WARNING | Slack `#andes-alerts` | Slack + Email on-call |
| INFO | Slack `#andes-ops` | Slack `#andes-ops` |

**Acción**: Configurar PagerDuty integration + Slack webhooks en CD pipeline.

### 9. ¿Log retention compliance (PCI-DSS)?
| Log Type | Requisito PCI-DSS | Actual | Gap |
|---|---|---|---|
| App logs | 1 año | 30 días + export S3 1 año | Export cubre |
| Auth logs (Keycloak) | 1 año | 90 días + export S3 1 año | Export cubre |
| Audit logs (K8s) | 1 año | 90 días + export S3 1 año | Export cubre |
| Kong access | 1 año | 30 días + export S3 1 año | Export cubre |

**Decisión**: Export a S3 (Glacier) 1 año cubre compliance. Verificar integridad export.

### 10. ¿Distributed Tracing: X-Ray vs Jaeger vs Zipkin?
| Opción | Pros | Contras | Decisión |
|---|---|---|---|
| **AWS X-Ray** | Integrado AWS, Service Map, nativo EKS | Vendor lock-in, costo traces | **Seleccionado** |
| Jaeger | Open source, CNCF, UI rico | Self-hosted, mantenimiento | Alternativa |
| Zipkin | Simple, ligero | Menos features | No |

**Decisión**: **AWS X-Ray** (ya en `tracing-config.md`). Revisar costos tras primer mes staging.

### 11. ¿Sampling rate para tracing?
| Entorno | Error sampling | Success sampling | Slow request sampling |
|---|---|---|---|
| Staging | 100% | 10% | 50% (>300ms) |
| Prod | 100% | 5% | 25% (>300ms) |

**Decisión**: Ajustar tras medir volumen real en staging.

### 12. ¿Exemplars en Prometheus para correlación trace-métrica?
**Respuesta**: **Sí, habilitado** en `application.yml` (`management.prometheus.metrics.export.exemplars.enabled: true`). Ver `tracing-config.md`.

### 13. ¿Anomaly Detection: CloudWatch vs Prometheus vs Lookout?
| Métrica | Método | Fase |
|---|---|---|
| AWS Infra (CPU, Mem, Conn) | CloudWatch Anomaly Detection | **Fase 1 (actual)** |
| App Golden Signals | Prometheus Rules (statistical) | **Fase 1 (actual)** |
| Multi-variate correlación | AWS Lookout for Metrics | **Fase 2** (opcional) |

**Decisión**: Fase 1 completa en `anomaly-config.md`. Lookout evaluar tras 1 mes staging.

### 13. ¿Alerting channels: PagerDuty, Slack, Email?
| Topic | ARN (placeholder) | Suscriptores staging | Suscriptores prod |
|---|---|---|---|
| `andes-critical` | `arn:aws:sns:us-east-1:111122223333:andes-critical` | Slack `#andes-alerts` | PagerDuty + Slack + SMS |
| `andes-warning` | `arn:aws:sns:us-east-1:111122223333:andes-warning` | Slack `#andes-alerts` | Slack + Email on-call |
| `andes-info` | `arn:aws:sns:us-east-1:111122223333:andes-info` | Slack `#andes-ops` | Slack `#andes-ops` |

**Acción**: Crear topics SNS + subscriptions en CDK (Security Stack o nuevo Observability Stack).

### 14. ¿Runbooks y documentación operacional?
| Runbook | Estado | Ubicación |
|---|---|---|
| Rollback App | ✅ Completo | `rollback-runbook.md` |
| Rollback Infra | ✅ Completo | `rollback-runbook.md` |
| Latency Drift | ⏳ Pendiente | Wiki (por crear) |
| CB Flapping | ⏳ Pendiente | Wiki (por crear) |
| Redis Failover | ⏳ Pendiente | Wiki (por crear) |
| Aurora Failover | ⏳ Pendiente | Wiki (por crear) |
| Keycloak Restart | ⏳ Pendiente | Wiki (por crear) |
| Anomaly Response | ✅ Completo | `anomaly-config.md` |

**Acción**: Crear wiki runbooks antes de primer deploy prod.

### 15. ¿Cost monitoring y budgets observabilidad?
| Componente | Costo estimado staging/mes | Budget alerta |
|---|---|---|
| CloudWatch Logs | ~$10-20 | 80% forecast |
| CloudWatch Metrics + Alarms | ~$10-15 | 80% forecast |
| CloudWatch Anomaly Detection | ~$5-10 | 80% forecast |
| X-Ray Traces | ~$10 | 80% forecast |
| Prometheus/Grafana (self-hosted) | $0 (en EKS) | N/A |
| **Total estimado** | **~$35-55/mes** | **Budget $100/mes** |

**Acción**: Configurar AWS Budgets + alertas 80% tras primer mes real.

### 16. ¿SLO Review cadence?
| Review | Frecuencia | Participantes | Output |
|---|---|---|---|
| **Weekly** | Lunes | Tech Lead, SRE | Error budget remaining, incidents |
| **Monthly** | 1ro mes | Tech Lead, EM, Product | Trends, capacity planning |
| **Quarterly** | Trimestral | Eng Director, CTO | Strategic review, target adjustment |

### 17. ¿Feature flags para observabilidad?
| Flag | Propósito | Default |
|---|---|---|
| `obs.tracing.enabled` | Activar/desactivar tracing | true |
| `obs.metrics.exemplars` | Exemplars en Prometheus | true |
| `obs.logs.debug` | Log level DEBUG temporal | false |
| `obs.anomaly.detection` | Anomaly detection rules | true |

**Implementación**: Spring Cloud Config / AWS AppConfig (preparado, activación via ConfigMap v1.0).

---

## Respuestas registradas (auditoría)

| Pregunta | Respuesta | Fecha | Responsable |
|---|---|---|---|
| Golden Signals | Latencia, Tráfico, Errores, Saturación | 2026-10-18 | Operations |
| SLOs | 9 SLOs definidos (latencia, disponibilidad, cache, degradación, infra) | 2026-10-18 | Operations |
| Dashboards | 4 dashboards (Golden Signals, Infra, Security, Business) | 2026-10-18 | Operations |
| Log Retention | 30d app, 90d auth/audit, 1yr S3 export | 2026-10-18 | Operations |
| Tracing | OpenTelemetry + X-Ray, 10%/100% sampling | 2026-10-18 | Operations |
| SLO Targets Prod | Pendiente validación con Product | — | Product/Eng |
| Error Budget Policy | Freeze deploys en prod al agotar | 2026-10-18 | Eng Manager |
| Alerting Channels | PagerDuty (crit), Slack (warn/info) | 2026-10-18 | Operations |
| Log Compliance | Export S3 1yr cubre PCI-DSS | 2026-10-18 | Compliance |
| Tracing Backend | AWS X-Ray (vendor lock-in aceptado) | 2026-10-18 | Operations |
| Sampling Rates | Staging 10/100/50, Prod 5/100/25 | 2026-10-18 | Operations |
| Exemplars | Habilitado (Prometheus + X-Ray) | 2026-10-18 | Operations |
| Anomaly Detection | CloudWatch + Prometheus Rules (Fase 1), Lookout Fase 2 | 2026-10-18 | Operations |
| Alert Channels | SNS topics + PagerDuty/Slack | 2026-10-18 | Operations |
| Runbooks | Rollback completos, otros pendientes wiki | 2026-10-18 | Operations |
| Cost Monitoring | AWS Budgets $100/mes baseline | 2026-10-18 | Finance/Eng |
| SLO Review Cadence | Weekly/Monthly/Quarterly | 2026-10-18 | Eng Manager |
| Feature Flags | 4 preparadas (Spring Cloud Config) | 2026-10-18 | Operations |