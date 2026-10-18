# Incident Response Plan — `skeleton-stock-query`

## Referencias upstream

| Artefacto | Ubicación | Qué aporta |
|---|---|---|
| `runbooks.md` | `operation/incident-response/runbooks.md` | 18 runbooks detallados |
| `alarms.md` | `operation/observability-setup/alarms.md` | Alarmas CRIT/WARN/INFO |
| `anomaly-config.md` | `operation/observability-setup/anomaly-config.md` | Detección anomalías, runbook respuesta |
| `reliability-design` | `construction/skeleton-stock-query/nfr-design/reliability-design.md` | CB/Bulkhead, degradación, RTO/RPO |
| `infrastructure-specification` | `construction/skeleton-stock-query/infrastructure-design/infrastructure-specification.md` | Specs infra, RTO/RPO |

---

## 1. Alcance y Objetivos

### Servicios Cubiertos
- **stock-price-adapter** (app principal)
- **Kong Ingress Controller** (edge)
- **Keycloak** (auth/OIDC)
- **ElastiCache Redis** (cache)
- **Aurora PostgreSQL** (Keycloak DB)
- **EKS Cluster** (plataforma)

### Objetivos
| Objetivo | Target |
|---|---|
| **MTTD** (Mean Time to Detect) | < 5 min |
| **MTTA** (Mean Time to Acknowledge) | < 5 min |
| **MTTR** (Mean Time to Resolve) | < 30 min (CRIT), < 60 min (WARN) |
| **RTO** (Recovery Time Objective) | < 15 min (app), < 30 min (infra) |
| **RPO** (Recovery Point Objective) | < 1 min (Aurora), < 5 min (Redis) |

### Severidad de Incidentes
| Nivel | Definición | Ejemplos | Respuesta |
|---|---|---|---|
| **SEV-1** (Critical) | Servicio caído, impacto masivo, pérdida datos | App down, Kong down, Keycloak down, Data loss | Inmediata (24/7), PagerDuty, War room |
| **SEV-2** (Major) | Degradación severa, funcionalidad crítica afectada | Latencia > SLO, CB open, Cache miss masivo | Urgente (horario laboral + on-call) |
| **SEV-3** (Minor) | Degradación menor, funcionalidad no crítica | Latencia elevada, cache hit ratio bajo, alerta anomalía | Próximo horario laboral |
| **SEV-4** (Low) | Cosmético, sin impacto usuario | Certificado por expirar en 30d, log noise | Próximo sprint |

---

## 2. Fases de Respuesta a Incidentes

### Fase 1: Detección y Alerta (0-5 min)
| Acción | Responsable | Herramienta |
|---|---|---|
| Alerta disparada | Sistema (CloudWatch/Prometheus) | CloudWatch, Prometheus Alertmanager |
| Notificación enviada | Alertmanager/SNS | Slack `#andes-alerts`, PagerDuty (CRIT) |
| On-call recibe notificación | On-call rotativo | PagerDuty app, Slack push |
| On-call acusa recibo (ack) | On-call | PagerDuty / Slack reaction |

**SLA**: Ack < 5 min desde alerta

### Fase 2: Triage y Clasificación (5-15 min)
| Acción | Responsable | Output |
|---|---|---|
| Verificar alerta (falso positivo?) | On-call | Confirmar / Descartar |
| Identificar servicio/s afectados | On-call | Servicio, componente |
| Determinar severidad (SEV-1/2/3/4) | On-call + Tech Lead (si duda) | SEV-1/2/3/4 |
| Consultar runbook correspondiente | On-call | Runbook ID |
| Iniciar incidente en tracker | On-call | Ticket INC-XXXX |

### Fase 3: Mitigación y Resolución (15-60 min)
| Acción | Responsable | Herramienta |
|---|---|---|
| Ejecutar runbook correspondiente | On-call | `runbooks.md` |
| Aplicar mitigación inmediata | On-call | kubectl, AWS CLI, rollback |
| Verificar mitigación efectiva | On-call | Métricas, health checks |
| Si no resuelto en 15 min → Escalar | On-call | Tech Lead / EM |

### Fase 4: Comunicación (Continuo)
| Momento | Canal | Audiencia | Plantilla |
|---|---|---|---|
| Inicio incidente | `#andes-alerts` | On-call, Tech Lead, SRE | `🚨 INCIDENTE SEV-X - <Título>` |
| Cada 15 min (SEV-1) | `#andes-incidents` | On-call, Tech Lead, SRE, EM | Update estado |
| Cada 30 min (SEV-2) | `#andes-incidents` | On-call, Tech Lead, SRE | Update estado |
| Resolución | `#andes-alerts` + `#andes-status` | Todo equipo, stakeholders | `✅ INCIDENTE RESUELTO` |
| Post-mortem (24h) | Ticket tracker | Tech Lead, EM, On-call | Post-mortem template |

### Fase 5: Post-Incidente (24h - 1 semana)
| Acción | Responsable | Plazo |
|---|---|---|
| Crear ticket post-mortem | On-call | < 24h |
| Completar post-mortem (5 Whys, action items) | On-call + Tech Lead | < 72h |
| Revisar y aprobar post-mortem | Tech Lead + EM | < 1 semana |
| Action items a sprint planning | Tech Lead | Próximo sprint |
| Actualizar runbooks/alertas | SRE | < 1 sprint |

---

## 3. Escenarios de Incidentes Mayores

### Escenario A: App Completamente Caída (SEV-1)
**Trigger**: Health checks fallando, ALB 502/503, 0 pods Ready
**Impacto**: 100% requests fallan
**RTO Target**: < 15 min

**Runbooks**: RB-005 (Health Checks), RB-008 (Kong), RB-005 (App)
**Pasos clave**:
1. Verificar pods: `kubectl get pods -n andes-retail`
2. Si 0 pods: `kubectl rollout restart deployment/stock-price-adapter`
3. Si CrashLoopBackOff: revisar logs, `kubectl rollout undo`
4. Si 0 nodos: `kubectl scale nodegroup --desired=2` (o esperar CA)
5. Verificar: health checks 200, smoke test PASS

### Escenario B: Degradación Severa Latencia (SEV-1/2)
**Trigger**: p95 > 500ms (CRIT) o p95 > 300ms sostenido (WARN)
**Impacto**: UX degradada, posible violación SLO
**RTO Target**: < 30 min

**Runbooks**: RB-003, RB-009, RB-010, RB-011
**Árbol decisión**: Cache hit? → Redis. SAP latency? → SAP. CPU? → Scale.

### Escenario C: Circuit Breaker Abierto (SEV-2)
**Trigger**: `CRIT-STOCK-CB-OPEN`
**Impacto**: App sirve stale-cache o 503, SAP no consultado
**RTO Target**: Auto-recovers en 60s (half-open) o < 15 min manual

### Escenario D: Redis Down (SEV-1)
**Trigger**: `CRIT-REDIS-UNAVAILABLE`
**Impacto**: Cache perdido, latencia ↑, carga SAP ↑
**RTO Target**: < 30 min (failover automático < 60s)
**Runbook**: RB-006

### Escenario E: Aurora/Keycloak Down (SEV-1)
**Trigger**: `CRIT-AURORA-UNAVAILABLE` + Keycloak down
**Impacto**: Auth totalmente roto (401 todos requests)
**RTO Target**: < 30 min (failover Aurora < 30s, Keycloak restart < 5 min)
**Runbooks**: RB-007

### Escenario F: Kong Down (SEV-1)
**Trigger**: `CRIT-KONG-UNAVAILABLE`
**Impacto**: 100% tráfico bloqueado (502/503 en ALB)
**RTO Target**: < 15 min
**Runbook**: RB-008

### Escenario G: PII Leakage (SEV-1 Compliance)
**Trigger**: `WARN-STOCK-PII-LEAKAGE` / Detección manual
**Impacto**: Violación PCI-DSS/GDPR
**RTO Target**: < 15 min contención, < 24h limpieza
**Runbook**: RB-018

---

## 4. Comunicación y Stakeholders

### Canales de Comunicación
| Canal | Propósito | Audiencia |
|---|---|---|
| `#andes-alerts` | Alertas automáticas, inicio/fin incidente | On-call, Tech Lead, SRE, EM |
| `#andes-incidents` | Coordinación incidente activo | On-call, Tech Lead, SRE, EM |
| `#andes-status` | Status page público/interno | Todo equipo, stakeholders |
| PagerDuty | Escalación SEV-1 | On-call rotativo |
| Status Page | Comunicación clientes | Clientes, partners |

### Plantillas de Comunicación
Ver `runbooks.md` sección "Comunicación Durante Incidentes"

### Status Page Updates
| Estado | Mensaje | Frecuencia |
|---|---|---|
| Investigando | "Investigando informes de latencia/errores en API stock" | Inicial |
| Identificado | "Identificada causa: Redis cache degradado. Aplicando mitigación." | Cada 15 min (SEV-1) |
| Mitigando | "Failover Redis iniciado. Restaurando cache." | Cada 10 min (SEV-1) |
| Monitoreando | "Redis recuperado. Verificando estabilidad 15 min." | Cada 15 min |
| Resuelto | "Incidente resuelto. Servicio restaurado. Post-mortem en 24h." | Final |

---

## 5. Herramientas y Accesos

### Herramientas Requeridas
| Herramienta | Propósito | Acceso Requerido |
|---|---|---|
| `kubectl` | K8s ops | `andes-staging` / `andes-prod` context |
| `aws cli` | AWS ops | `andes-staging` / `andes-prod` profile |
| `helm` | Helm releases | `andes-staging` / `andes-prod` context |
| `aws console` | CloudWatch, RDS, ElastiCache, EKS | IAM Role `OnCallRole` |
| PagerDuty | Alerting, on-call | Account `andes-retail` |
| Slack | Comunicación | Workspace `andes-retail` |
| GitHub | Deploy logs, rollback | Repo `andes-retail/interop` |

### Accesos On-Call (Mínimos)
| Recurso | Permiso | Justificación |
|---|---|---|
| EKS `kubectl` | `get`, `describe`, `logs`, `rollout restart/undo`, `scale` | Mitigación app |
| AWS EKS | `DescribeCluster`, `DescribeNodegroup` | Verificar cluster |
| AWS ElastiCache | `DescribeReplicationGroups`, `FailoverReplicationGroup` | Failover Redis |
| AWS RDS | `DescribeDBClusters`, `FailoverDBCluster` | Failover Aurora |
| AWS EKS/EC2 | `DescribeInstances`, `TerminateInstances` (nodes) | Node replacement |
| CloudWatch | `GetMetricStatistics`, `StartQuery` | Diagnóstico |
| Logs Insights | `StartQuery`, `GetQueryResults` | Diagnóstico logs |
| Secrets Manager | `GetSecretValue` (solo lectura) | Verificar secrets |

---

## 6. Métricas de Efectividad (KPIs)

| KPI | Target | Medición |
|---|---|---|
| **MTTD** | < 5 min | Alerta → On-call aware |
| **MTTA** | < 5 min | Alerta → On-call ack |
| **MTTR (SEV-1)** | < 30 min | Alerta → Resuelto |
| **MTTR (SEV-2)** | < 60 min | Alerta → Resuelto |
| **RTO App** | < 15 min | Caída → Restaurado |
| **RTO Infra** | < 30 min | Caída → Restaurado |
| **RPO Aurora** | < 1 min | PITR |
| **RPO Redis** | < 5 min | Snapshot diario |
| **Post-mortem completion** | < 72h | Ticket creado → Completado |
| **Action items closure** | < 1 sprint | Sprint planning → Done |

---

## 5. Mejora Continua

### Revisiones Periódicas
| Revisión | Frecuencia | Participantes | Output |
|---|---|---|---|
| **Incident Review** | Semanal (post-incidentes) | On-call, Tech Lead, SRE | Lessons learned, runbook updates |
| **Runbook Review** | Mensual | SRE, Tech Lead | Runbook accuracy, gaps |
| **Alert Tuning** | Mensual | SRE, Tech Lead | False positive/negative reduction |
| **Game Day / Chaos** | Trimestral | SRE, Tech Lead, Devs | Resilience validation |
| **Post-Mortem Review** | Mensual | Tech Lead, EM | Action items closure rate |

### Game Days / Chaos Engineering (Trimestral)
| Experimento | Objetivo | Frecuencia |
|---|---|---|
| Pod kill (app) | Validar HA, rollout, HPA | Trimestral |
| Redis failover | Validar failover automático, app degradation | Trimestral |
| Aurora failover | Validar failover, Keycloak recovery | Trimestral |
| Kong restart | Validar ingress recovery | Trimestral |
| Network partition | Validar CB/Bulkhead behavior | Semestral |
| AZ outage (simulated) | Validar Multi-AZ | Anual |

---

## 6. Compliance y Auditoría

### Registros Requeridos
| Registro | Retención | Ubicación |
|---|---|---|
| Incident tickets | 7 años | Jira/Linear/GitHub Issues |
| Alert history | 1 año | CloudWatch/PagerDuty |
| Chat logs (Slack) | 1 año | Slack export |
| Post-mortems | 7 años | Confluence/Notion/GitHub |
| Runbook versions | Historial completo | Git (este repo) |
| Access logs (kubectl, AWS) | 1 año | CloudTrail |

### Auditoría PCI-DSS
- Incident response plan documentado ✅
- 24/7 monitoring ✅
- Escalation procedures ✅
- Post-incidente root cause ✅
- Annual test (Game Day) ✅ (planificado trimestral)