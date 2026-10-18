# Incident Response Questions — `skeleton-stock-query`

## Preguntas de clarificación (resueltas)

### 1. ¿Cuáles son los modos de fallo más probables?
**Respuesta**: Documentados en `runbooks.md` (18 runbooks):
- App down (pods crash, OOM, deploy bug)
- Kong/Ingress down
- Keycloak/Auth down (Aurora failover)
- Redis down (cache perdido, degradación SAP)
- Aurora down (Keycloak inoperativo)
- Latencia degradada (Redis, SAP, CPU, GC)
- Circuit Breaker / Bulkhead (SAP issues)
- PII Leakage (compliance)

### 2. ¿Cuáles son las rutas de escalación y rotaciones on-call?
**Respuesta**: Documentado en `escalation-matrix.md`:
- 4 niveles (L1 On-call → L2 Tech Lead → L3 EM → L4 CTO)
- Rotación semanal On-call (primario + secundario)
- SLA: Ack < 5 min, Escalation cada 15 min (SEV-1) / 30 min (SEV-2)
- Canales: PagerDuty → Slack `#andes-alerts` → `#andes-incidents` → Email/Call

### 3. ¿Qué remediación automatizada es posible?
**Respuesta**: 
- **Ya implementado**: Circuit Breaker auto-recovery (half-open 60s), Redis Multi-AZ failover (< 60s), Aurora failover (< 30s), HPA scale
- **Runbooks automatizables (SSM Automation - Fase 2)**: Redis failover, Aurora failover + Keycloak restart, Kong restart, App rollback
- **No automatizado (requiere juicio)**: Root cause diagnosis, rollback decision, external comms

### 4. ¿Procedimientos de comunicación durante incidentes?
**Respuesta**: Documentados en `incident-plan.md` y `runbooks.md`:
- Canales: `#andes-alerts` (alertas), `#andes-incidents` (coordinación), `#andes-status` (público), PagerDuty (escalation)
- Plantillas: Inicio, updates cada 15/30 min, resolución, post-mortem
- Status page updates para clientes
- Escalation matrix con plantillas L1→L2→L3→L4

### 4. ¿RTO/RPO targets?
**Respuesta**: Documentados en `incident-plan.md` y `reliability-design`:
| Componente | RTO | RPO |
|---|---|---|
| App (stock-price-adapter) | < 15 min | N/A (stateless) |
| Kong Ingress | < 15 min | N/A |
| Keycloak | < 30 min | N/A (stateless, BD en Aurora) |
| Redis ElastiCache | < 30 min (failover auto < 60s) | < 5 min (snapshot diario) |
| Aurora PostgreSQL | < 30 min (failover auto < 30s) | < 1 min (PITR) |
| EKS Cluster | < 30 min | N/A (infra as code) |

---

## Preguntas abiertas / decisiones pendientes

### 5. ¿Rotación On-Call definitiva?
| Pregunta | Estado | Decisión pendiente |
|---|---|---|
| ¿Tamaño equipo on-call? | Pendiente | Mínimo 6 ingenieros para rotación semanal 24/7 sin burnout |
| ¿Compensación on-call? | Pendiente | Definir con HR/Finance (pago por guardia + tiempo compensatorio) |
| ¿Herramienta rotación? | PagerDuty | Confirmar schedule `andes-oncall` configurado |
| ¿Secundario obligatorio? | Sí | Siempre 2 on-call (primario + backup) |

### 6. ¿Integración AWS Incident Manager?
| Pregunta | Estado | Decisión |
|---|---|---|
| ¿Usar Incident Manager para SEV-1? | Fase 2 | Evaluar tras primer incidente real staging |
| ¿Runbooks SSM Automation? | Fase 2 | 4 candidatos: Redis failover, Aurora failover, Kong restart, App rollback |
| ¿Timeline automático para post-mortem? | Sí | Incident Manager export → post-mortem template |

### 6. ¿Game Days / Chaos Engineering?
| Experimento | Frecuencia | Estado | Prerequisitos |
|---|---|---|---|
| Pod kill (app) | Trimestral | Planificado | Runbook RB-005 validado |
| Redis failover | Trimestral | Planificado | Runbook RB-006 validado, staging |
| Aurora failover | Trimestral | Planificado | Runbook RB-007 validado, staging |
| Kong restart | Trimestral | Planificado | Runbook RB-008 validado |
| Network partition (CB/Bulkhead) | Semestral | Planificado | Requiere tooling (Litmus/Chaos Mesh) |
| AZ outage simulado | Anual | Planificado | Requiere Multi-AZ staging, DR plan |

### 7. ¿Post-Mortem Process?
| Pregunta | Respuesta | Detalle |
|---|---|---|
| ¿Template? | Sí | En `incident-plan.md` (5 Whys, action items) |
| ¿Plazo post-mortem? | 72h para borrador, 1 semana aprobado | Ticket en Jira/Linear |
| ¿Quién aprueba? | Tech Lead + EM | Action items a sprint planning |
| ¿Blameless? | Sí | Cultura blameless, focus en sistema no persona |
| ¿Publicación interna? | Sí | Wiki/Confluence, accesible a todo engineering |

### 7. ¿Métricas de Efectividad (KPIs)?
| KPI | Target | Medición |
|---|---|---|
| MTTD | < 5 min | Alerta → On-call aware (PagerDuty timestamp) |
| MTTA | < 5 min | Alerta → Ack (PagerDuty ack timestamp) |
| MTTR SEV-1 | < 30 min | Alerta → Resuelto (ticket closed) |
| MTTR SEV-2 | < 60 min | Alerta → Resuelto |
| RTO App | < 15 min | Caída → Health checks PASS |
| RTO Infra | < 30 min | Caída → Recursos healthy |
| RPO Aurora | < 1 min | PITR verified |
| RPO Redis | < 5 min | Snapshot diario |
| Post-mortem completion | < 72h | Ticket creado → Aprobado |
| Action items closure | < 1 sprint | Sprint planning → Done |

### 8. ¿Automatización Remediación (SSM Automation)?
| Runbook | Automatizable | Complejidad | Fase |
|---|---|---|---|
| RB-001 (CB Open) | Parcial (verify SAP, restart pods) | Media | Fase 2 |
| RB-002 (Bulkhead) | Parcial (scale pods, verify SAP) | Media | Fase 2 |
| RB-006 (Redis Down) | **Sí** (failover ElastiCache) | Baja | **Fase 2 prioritario** |
| RB-007 (Aurora Down) | **Sí** (failover Aurora + restart Keycloak) | Media | **Fase 2 prioritario** |
| RB-008 (Kong Down) | **Sí** (restart deployment) | Baja | **Fase 2 prioritario** |
| RB-003/009 (Latencia) | No (requiere diagnóstico) | Alta | No |
| RB-018 (PII Leakage) | No (requiere código fix) | Alta | No |

### 9. ¿Integración con AWS Incident Manager?
| Característica | Estado | Comentario |
|---|---|---|
| Response Plans | Fase 2 | 8 planes (RB-001 a RB-008) |
| Engagements Auto | Fase 2 | CloudWatch Alarms → Incident Manager |
| SSM Automation Runbooks | Fase 2 | 4 priorizados (Redis, Aurora, Kong, App rollback) |
| Timeline Auto | Fase 2 | Export a post-mortem template |
| ChatOps (Slack) | Fase 2 | `/incident` commands |

### 10. ¿Compliance y Auditoría Incident Response?
| Requisito | Estado | Evidencia |
|---|---|---|
| Plan documentado | ✅ | `incident-plan.md`, `runbooks.md`, `escalation-matrix.md` |
| 24/7 Monitoring | ✅ | CloudWatch + PagerDuty 24/7 |
| Escalation Procedures | ✅ | `escalation-matrix.md` |
| Post-Incident Root Cause | ✅ | Template en `incident-plan.md` |
| Annual Test (Game Day) | Planificado trimestral | Primer Game Day post-staging deploy |
| Training On-Call | Pendiente | Onboarding checklist por definir |
| Communication Plan | ✅ | `incident-plan.md` sección comunicación |

### 11. ¿Integración con Security Incident Response?
| Escenario | Coordinación | Responsable |
|---|---|---|
| PII Leakage (RB-018) | Security Officer + Legal + CTO | CTO notifica en 72h (GDPR) / inmediato (PCI) |
| Credenciales comprometidas (Secrets) | Security + EM | Rotación inmediata secrets, auditoría acceso |
| Ataque DDoS / Rate Limit Abuso | SRE + Security | WAF rules, Kong rate limit adjust |
| Vulnerabilidad 0-day (dependencia) | Security + Tech Lead | Patch inmediato, deploy emergency |

### 11. ¿Métricas de Madurez Incident Response?
| Métrica | Target | Medición |
|---|---|---|
| **MTTD** | < 5 min | PagerDuty alert timestamp → on-call aware |
| **MTTA** | < 5 min | Alert timestamp → ack timestamp |
| **MTTR SEV-1** | < 30 min | Alert → resolved (ticket closed) |
| **MTTR SEV-2** | < 60 min | Alert → resolved |
| **Escalation Rate** | < 20% SEV-1 escalan a L3 | L1→L3 / total SEV-1 |
| **False Positive Rate** | < 10% | Alerts dismissed / total alerts |
| **Recurring Incident Rate** | 0% mismo root cause en 90d | Post-mortem tags |
| **Action Item Closure** | 100% en 1 sprint | Jira/Linear tickets Done |
| **Runbook Accuracy** | 100% pasos válidos | Game Day validation |
| **On-Call Burnout** | 0 incidentes > 2h sin relevo | PagerDuty schedule adherence |

---

## Respuestas registradas (auditoría)

| Pregunta | Respuesta | Fecha | Responsable |
|---|---|---|---|
| Modos fallo probables | 18 runbooks documentados | 2026-10-18 | Operations |
| Escalation paths | 4 niveles, rotación semanal, SLA 15/30/60 min | 2026-10-18 | Operations |
| Remediación automatizada | 4 runbooks automatizables Fase 2 (Redis, Aurora, Kong, App) | 2026-10-18 | Operations |
| Comunicación | 4 canales (alerts, incidents, status, PagerDuty), plantillas definidas | 2026-10-18 | Operations |
| RTO/RPO | App 15min, Infra 30min, Aurora 1min, Redis 5min | 2026-10-18 | Operations |
| On-Call Rotation | Semanal, 2 ingenieros (primario/backup), PagerDuty | 2026-10-18 | EM/HR |
| AWS Incident Manager | Fase 2 (8 response plans, 4 SSM automations) | 2026-10-18 | Operations |
| Game Days | Trimestral (pod kill, Redis, Aurora, Kong) | 2026-10-18 | SRE |
| Post-Mortem | 72h borrador, 1 semana aprobado, blameless | 2026-10-18 | Tech Lead |
| KPIs | MTTD<5m, MTTA<5m, MTTR<30m, Escalation<20% | 2026-10-18 | Operations |
| SSM Automation | 4 priorizados Fase 2 | 2026-10-18 | Operations |
| Security IR | Coordinación CTO/Legal para PII, Security para creds/DDoS | 2026-10-18 | Security/CTO |
| Madurez IR KPIs | 10 métricas definidas con targets | 2026-10-18 | Operations |