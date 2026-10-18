# Feedback & Optimization Questions — `skeleton-stock-query`

## Preguntas de clarificación (resueltas)

### 1. ¿Se están cumpliendo los SLOs?
**Respuesta**: **Pendiente validación en producción** — Los SLOs están definidos en `slo-config.md` (9 SLOs: latencia, disponibilidad, cache hit, degradación, infra). La validación real requiere 30 días de operación en producción con datos reales de tráfico. Load tests en staging (pendientes) darán indicadores tempranos.

### 2. ¿Hay oportunidades de optimización de costos?
**Respuesta**: **Sí, documentadas en `cost-analysis.md`** — Principales: Savings Plans (30-50% compute), Spot Instances para workloads tolerantes, Right-sizing Aurora/Redis, Data Transfer optimization. Ahorro estimado 20-30% en prod.

### 3. ¿Hay configuration drift?
**Respuesta**: **Pendiente detección post-deploy** — `drift-report.md` define metodología (AWS Config, CDK Diff, K8s Diff, Helm Diff) y pipeline automatizado semanal. Pre-deploy: 0 drifts (sin entorno real).

### 4. ¿Qué patrones de uso sugieren nuevas features o issues?
**Respuesta**: **Basado en diseño y load test plan** — Patrones Zipf en SKUs (20/80), picos horarios, degradación controlada via stale-cache, rate limiting por canal. Patrones reales emergentes tras 30 días prod alimentarán próxima Ideation.

### 5. ¿Qué toil operacional puede automatizarse?
**Respuesta**: **Identificado en `incident-plan.md` y `feedback-loop.md`**:
- SSM Automation para Redis/Aurora failover, Kong restart, App rollback (Fase 2)
- GitOps (ArgoCD) para drift auto-remediation
- Chaos Engineering programado (Game Days trimestrales)
- Cost Anomaly Detection (AWS Cost Anomaly Detection)

---

## Preguntas abiertas / decisiones pendientes

### 6. ¿Próxima unidad de trabajo (Next Intent)?
| Candidata | Descripción | Valor | Dependencias | Decisión Pendiente |
|---|---|---|---|---|
| **`stock-reservation`** | `POST/DELETE /v1/inventory/reserve` (FR-06) | Habilita checkout, order mgmt | FR-01 done, FR-06 deferred | **Alta** (roadmap checkout) |
| **`multi-tenant-auth`** | Realm por marca, federación SAML/OIDC, user provisioning (FR-11 full) | Escalabilidad auth | FR-11 base done | **Alta** (roadmap multi-brand) |
| **`event-driven-stock`** | Kafka topic `stock.changed`, consumers WMS/OMS/Analytics | Real-time sync | FR-01 done | Media |
| **`observability-full`** | Dashboards validados, alertas afinadas, ML anomaly detection | Operabilidad completa | Observabilidad base done | Media |

### 7. ¿Validación de SLOs en Producción?
| Pregunta | Estado | Decisión |
|---|---|---|
| ¿30 días observación antes de certificar SLOs? | **Sí** | Estándar: 30 días rolling window |
| ¿Error Budget Policy enforcement? | **Sí, en prod** | Freeze deploys al agotar 90% budget |
| ¿Quién autoriza override de budget? | **Engineering Manager** | Tech Lead propone, EM aprueba |
| ¿SLO targets ajustables? | **Trimestral review** | Ajustar si arquitectura cambia |

### 8. ¿Estrategia de Costos en Producción?
| Pregunta | Recomendación | Decisión |
|---|---|---|
| ¿Savings Plans 1yr o 3yr? | **1yr inicial** (flexibilidad) | Comprar tras 1 mes baseline |
| ¿Spot Instances para nodes? | **Sí, para workloads batch/non-critical** | Configurar HPA + tolerations |
| ¿Right-sizing Aurora/Redis? | **Tras 1 mes baseline** | Compute Optimizer + métricas reales |
| ¿Multi-AZ obligatorio? | **Sí (ya implementado)** | Redis Multi-AZ, Aurora Serverless v2 Multi-AZ |

### 9. ¿Drift Detection & Remediation?
| Pregunta | Respuesta | Acción |
|---|---|---|
| ¿Frecuencia drift detection? | **Semanal (automatizado)** + Pre-deploy | GitHub Actions + AWS Config |
| ¿Auto-remediation habilitado? | **Sí, para Config Rules CRITICAL** | `restricted-ssh`, `encrypted-volumes`, etc. |
| ¿Drift SEV-1 auto-incidente? | **Sí** | Drift CRITICAL → SEV-2 auto |
| ¿Drift dashboard? | **Fase 2** | Añadir a `AndesRetail/Infrastructure/Overview` |

### 10. ¿Feedback Loop a Próxima Ideation?
| Input | Formato | Frecuencia |
|---|---|---|
| **SLO Compliance Report** | `slo-report.md` actualizado | Mensual |
| **Cost Optimization Report** | `cost-analysis.md` actualizado | Trimestral |
| **Drift Report** | `drift-report.md` actualizado | Semanal (auto) + Post-mortem |
| **Incident Lessons Learned** | `incident-plan.md` + post-mortems | Post-incidente |
| **Performance Trends** | `nfr-validation-matrix.md` actualizado | Trimestral |
| **User Feedback / Feature Requests** | Jira/Linear tickets | Continuo |

### 11. ¿Nuevas Capabilities Prioritarias?
| Capability | Driver | Esfuerzo | ROI |
|---|---|---|---|
| **Stock Reservation API** (FR-06) | Checkout enablement | Alto | **Muy Alto** |
| **Multi-Tenant Auth** | Multi-brand expansion | Muy Alto | **Muy Alto** |
| **Event-Driven Stock Sync** | WMS/OMS integration | Medio | Alto |
| **GraphQL API** | Unified consumer experience | Alto | Medio |
| **ML Demand Forecasting** | Inventory optimization | Muy Alto | Medio |

### 12. ¿Platform Evolution?
| Decisión | Opciones | Recomendación | Timeline |
|---|---|---|---|
| **GitOps (ArgoCD/Flux)** | ArgoCD vs Flux vs manual | **ArgoCD** (maduro, UI, RBAC) | Q1 2026 |
| **Service Mesh** | Istio vs Linkerd vs none | **Istio** (si multi-cluster) | Fase 2 |
| **Multi-Region DR** | Active-Passive us-west-2 | **Aurora Global + Redis Global + Route53** | Q2 2026 |
| **Chaos Engineering** | Chaos Mesh vs Litmus | **Chaos Mesh** (K8s native) | Q1 2026 |
| **Service Mesh vs API Gateway** | Istio vs Kong vs API GW | **Kong + Istio sidecar** (hybrid) | Fase 2 |

### 13. ¿Team Scaling & On-Call?
| Pregunta | Respuesta | Acción |
|---|---|---|
| ¿Tamaño equipo para 24/7 on-call? | **Mínimo 6 ingenieros** (rotación semanal 2) | Contratar / redistribuir |
| ¿Compensación on-call? | **Definir con HR/Finance** | Pago por guardia + tiempo compensatorio |
| ¿Runbook ownership? | **Por componente** (App: Backend, Infra: Platform, Auth: Security) | Asignar owners en Confluence |
| ¿Game Days frequency? | **Trimestral** (pod kill, Redis, Aurora, Kong) | Calendarizar 2026 |

### 14. ¿Compliance & Audit Readiness?
| Requisito | Estado | Gap | Acción |
|---|---|---|---|
| PCI-DSS CDE Isolation | ✅ Diseñado (subnets data sin IGW) | Validar post-deploy | Pen-test Q1 2026 |
| KMS Rotation Anual | ✅ Habilitado | Monitorear | CloudWatch Alarm |
| Secrets Rotation | ⚠️ Manual (SAP, Keycloak) | Automatizar Lambda/EventBridge | Q1 2026 |
| Audit Logging (CloudTrail) | ✅ Org-level | Verificar log group | Verificar post-deploy |
| Config Rules Compliance | ✅ 10 reglas obligatorias | Auto-remediation habilitada | Verificar post-deploy |
| Data Retention (Logs 1yr) | ✅ S3 Glacier export | Verificar lifecycle | Verificar post-deploy |

### 15. ¿User Feedback Channels?
| Canal | Estado | Owner | Frecuencia Review |
|---|---|---|---|
| **Jira/Linear Tickets** | Activo | Product | Semanal (sprint planning) |
| **Slack `#andes-feedback`** | Pendiente crear | Product | Diario |
| **NPS/CSAT Surveys** | Pendiente | Product | Trimestral |
| **API Consumer Forum** | Pendiente | DevRel | Mensual |
| **Incident Post-Mortems** | Template listo | SRE/Tech Lead | Post-incidente |

---

## Respuestas registradas (auditoría)

| Pregunta | Respuesta | Fecha | Responsable |
|---|---|---|---|
| SLOs cumplidos | Pendiente validación prod (30 días) | 2026-10-18 | SRE |
| Cost optimization | Savings Plans, Spot, Right-sizing (20-30% ahorro) | 2026-10-18 | FinOps/Platform |
| Drift detection | Pipeline semanal + AWS Config + CDK/K8s/Helm diff | 2026-10-18 | Platform |
| Usage patterns | Zipf SKUs, picos horarios, stale-cache, rate limiting | 2026-10-18 | QA/Analytics |
| Toil automation | SSM Automation (4 runbooks Fase 2), GitOps, Chaos Engineering | 2026-10-18 | Platform |
| Next Intent | `stock-reservation` (FR-06) o `multi-tenant-auth` (FR-11) | 2026-10-18 | Product |
| SLO Validation | 30 días prod, error budget policy, EM override | 2026-10-18 | SRE/EM |
| Cost Strategy | Savings Plans 1yr, Spot, Right-sizing post-baseline | 2026-10-18 | FinOps |
| Drift Remediation | Auto para CRIT, semanal scan, auto-incidente | 2026-10-18 | Platform |
| Feedback to Ideation | SLO/Cost/Drift/Incident/Performance reports | 2026-10-18 | Product/Tech Lead |
| Next Capabilities | FR-06 (Stock Reservation) #1, FR-11 Full #2 | 2026-10-18 | Product |
| Platform Evolution | ArgoCD Q1, Istio Fase 2, DR Q2, Chaos Mesh Q1 | 2026-10-18 | Platform |
| Team Scaling | 6 engineers min para 24/7, HR/Finance on-call pay | 2026-10-18 | EM/HR |
| Compliance | PCI-DSS CDE ✅, KMS ✅, Secrets rotation pending, Config ✅ | 2026-10-18 | Security |
| User Feedback | Jira + Slack channel + NPS quarterly + Consumer forum | 2026-10-18 | Product/DevRel |