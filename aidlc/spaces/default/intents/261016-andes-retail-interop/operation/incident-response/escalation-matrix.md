# Escalation Matrix — `skeleton-stock-query`

## Referencias upstream

| Artefacto | Ubicación | Qué aporta |
|---|---|---|
| `incident-plan.md` | `operation/incident-response/incident-plan.md` | Fases respuesta, severidades, comunicación |
| `runbooks.md` | `operation/incident-response/runbooks.md` | 18 runbooks, procedimientos mitigación |
| `alarms.md` | `operation/observability-setup/alarms.md` | Alarmas CRIT/WARN/INFO con runbook links |
| `reliability-design` | `construction/skeleton-stock-query/nfr-design/reliability-design.md` | RTO/RPO, degradación controlada |

---

## 1. Matriz de Escalación por Severidad

### SEV-1 (Critical) — Servicio Caído / Impacto Masivo

| Tiempo sin respuesta | Nivel | Acción | Responsable | Contacto |
|---|---|---|---|---|
| **0 min** | **L1** | Alerta recibida, ack, empieza investigación | **On-call** (rotativo) | PagerDuty → `@oncall` |
| **15 min** | **L2** | Sin ack / sin progreso diagnóstico → Tech Lead se une, decide rollback | **Tech Lead** | Slack `@tech-lead`, PagerDuty `andes-tech-lead` |
| **30 min** | **L3** | Sin resolución / mitigación inefectiva → EM autoriza rollback mayor, comunica stakeholders | **Engineering Manager** | Slack `@eng-manager`, Email |
| **60 min** | **L4** | Sin resolución / impacto masivo extendido → CTO decide estrategia, comunicación externa | **CTO** | Slack `@cto`, Teléfono |

**SLA SEV-1**: MTTR < 30 min | Ack < 5 min | Escalation automática cada 15 min

### SEV-2 (Major) — Degradación Severa Funcionalidad Crítica

| Tiempo sin respuesta | Nivel | Acción | Responsable | Contacto |
|---|---|---|---|---|
| **0 min** | **L1** | Alerta recibida, ack, empieza investigación | **On-call** (rotativo) | PagerDuty → `@oncall` |
| **30 min** | **L2** | Sin progreso / diagnóstico incierto → Tech Lead revisa, sugiere runbook alternativo | **Tech Lead** | Slack `@tech-lead` |
| **60 min** | **L3** | Sin resolución → EM informado, decide si movilizar más recursos | **Engineering Manager** | Slack `@eng-manager` |
| **120 min** | **L4** | Sin resolución → CTO informado, evalúa impacto negocio | **CTO** | Slack `@cto` |

**SLA SEV-2**: MTTR < 60 min | Ack < 5 min | Escalation cada 30 min

### SEV-3 (Minor) — Degradación Menor / Funcionalidad No Crítica

| Tiempo sin respuesta | Nivel | Acción | Responsable | Contacto |
|---|---|---|---|---|
| **0 min** | **L1** | Alerta recibida, ack, triage | **On-call** (rotativo) | Slack `@oncall` |
| **4 horas** | **L2** | Sin progreso (horario laboral) → Tech Lead revisa en próximo día hábil | **Tech Lead** | Slack `@tech-lead` |
| **24 horas** | **L3** | Sin resolución → EM prioriza en sprint planning | **Engineering Manager** | Slack `@eng-manager` |

**SLA SEV-3**: MTTR < 8h (horario laboral) | Ack < 30 min | Escalation next business day

### SEV-4 (Low) — Cosmético / Sin Impacto Usuario

| Tiempo sin respuesta | Nivel | Acción | Responsable | Contacto |
|---|---|---|---|---|
| **0 min** | **L1** | Ticket creado, triage | **On-call** / Dev de guardia | Slack `@oncall` |
| **Próximo sprint** | **L2** | Tech Lead prioriza en backlog | **Tech Lead** | Sprint planning |

---

## 2. Matriz de Escalación por Tipo de Incidente

| Tipo Incidente | SEV Inicial | L1 (On-call) | L2 (Tech Lead) | L3 (EM) | L4 (CTO) | Runbook |
|---|---|---|---|---|---|---|
| **App Down** (0 pods Ready) | SEV-1 | On-call | 15 min | 30 min | 60 min | RB-005 |
| **Kong Down** | SEV-1 | On-call | 15 min | 30 min | 60 min | RB-008 |
| **Keycloak Down** | SEV-1 | On-call | 15 min | 30 min | 60 min | RB-007 |
| **Redis Down** | SEV-1 | On-call | 15 min | 30 min | 60 min | RB-006 |
| **Aurora Down** | SEV-1 | On-call | 15 min | 30 min | 60 min | RB-007 |
| **Latencia p95 > 500ms** | SEV-1 | On-call | 15 min | 30 min | 60 min | RB-003 |
| **Latencia p95 > 300ms (sostenido)** | SEV-2 | On-call | 30 min | 60 min | 120 min | RB-009 |
| **CB Open** | SEV-2 | On-call | 30 min | 60 min | 120 min | RB-001 |
| **Bulkhead Exhausted** | SEV-2 | On-call | 30 min | 60 min | 120 min | RB-002 |
| **Cache Hit Ratio < 50%** | SEV-2 | On-call | 30 min | 60 min | 120 min | RB-010 |
| **SAP Latencia > 250ms (sostenido)** | SEV-2 | On-call | 30 min | 60 min | 120 min | RB-011 |
| **Redis CPU/Mem Alto** | SEV-2 | On-call | 30 min | 60 min | 120 min | RB-012 |
| **Aurora CPU/Conn Alto** | SEV-2 | On-call | 30 min | 60 min | 120 min | RB-013 |
| **CB Flapping** | SEV-2 | On-call | 30 min | 60 min | 120 min | RB-014 |
| **Tráfico Anómalo Spike/Drop** | SEV-2 | On-call | 30 min | 60 min | 120 min | RB-015 |
| **Rate Limiting Sostenido** | SEV-2 | On-call | 30 min | 60 min | 120 min | RB-016 |
| **Cert Expiry < 30d** | SEV-3 | On-call | Next biz day | 24h | — | RB-017 |
| **PII Leakage** | SEV-1 | On-call | 15 min | 30 min | 60 min | RB-018 |

---

## 3. Contactos de Escalación (Placeholders)

### Rotación On-Call (Semanal)
| Semana | On-Call Primario | On-Call Secundario | Inicio | Fin |
|---|---|---|---|---|
| 1 | Dev A | Dev B | Lunes 00:00 UTC | Domingo 23:59 UTC |
| 2 | Dev C | Dev D | Lunes 00:00 UTC | Domingo 23:59 UTC |
| 3 | Dev E | Dev F | Lunes 00:00 UTC | Domingo 23:59 UTC |
| 4 | Dev A | Dev C | Lunes 00:00 UTC | Domingo 23:59 UTC |

> **Nota**: Rotación real definida en PagerDuty schedule `andes-oncall`.

### Contactos Clave

| Rol | Nombre | Slack | PagerDuty | Email | Teléfono (solo L3/L4) |
|---|---|---|---|---|---|
| **On-Call Primario** | Rotativo | `@oncall` | `andes-oncall` | `oncall@andes-retail.com` | — |
| **On-Call Secundario** | Rotativo | `@oncall-backup` | `andes-oncall-backup` | `oncall-backup@andes-retail.com` | — |
| **Tech Lead** | — | `@tech-lead` | `andes-tech-lead` | `tech-lead@andes-retail.com` | +XX XXX XXXX |
| **SRE Lead** | — | `@sre-lead` | `andes-sre` | `sre-lead@andes-retail.com` | +XX XXX XXXX |
| **Engineering Manager** | — | `@eng-manager` | — | `eng-manager@andes-retail.com` | +XX XXX XXXX |
| **CTO** | — | `@cto` | — | `cto@andes-retail.com` | +XX XXX XXXX |
| **Security Officer** | — | `@sec-officer` | — | `security@andes-retail.com` | +XX XXX XXXX |
| **Legal/Compliance** | — | `@legal` | — | `legal@andes-retail.com` | +XX XXX XXXX |
| **AWS Support** | Enterprise | — | — | `aws-support@andes-retail.com` | 1-800-AWS-SUPP |

---

## 4. Canales de Comunicación por Nivel

| Nivel de Escalación | Canal Principal | Canal Secundario | Audiencia |
|---|---|---|---|
| **L1 (On-call)** | PagerDuty push + Slack `#andes-alerts` | Slack DM | On-call, Tech Lead |
| **L2 (Tech Lead)** | Slack `#andes-incidents` + Call (Huddle/Zoom) | PagerDuty | Tech Lead, SRE Lead, On-call |
| **L3 (EM)** | Slack `#andes-incidents` + Email | Call | EM, Tech Lead, CTO (si L4) |
| **L4 (CTO)** | Call directo + Email ejecutivo | Slack `#andes-status` | CTO, EM, Legal, Comms, CEO |

### Plantillas de Notificación por Nivel

#### L1 → L2 (15 min SEV-1 / 30 min SEV-2)
```
🔴 ESCALACIÓN L1→L2 - INC-XXXX
Severidad: SEV-1/SEV-2
Servicio: stock-price-adapter
Trigger: <alarma>
Tiempo sin progreso: 15/30 min
On-call: @usuario
Acciones intentadas: <runbook steps>
Petición: Revisión / Decisión rollback / Recursos extra
```

#### L2 → L3 (30 min SEV-1 / 60 min SEV-2)
```
🟠 ESCALACIÓN L2→L3 - INC-XXXX
Severidad: SEV-1/SEV-2
Servicio: stock-price-adapter
Tiempo total: XX min
Causa sospechada: <hipótesis>
Acciones tomadas: <runbook steps + decisiones>
Riesgo: <impacto si no resuelve en 15/30 min>
Decisión necesaria: Rollback mayor / Recursos extra / Comunicación clientes
```

#### L3 → L4 (60 min SEV-1 / 120 min SEV-2)
```
🟡 ESCALACIÓN L3→L4 - INC-XXXX
Severidad: SEV-1
Servicio: stock-price-adapter
Tiempo total: 60 min
Impacto negocio: <clientes afectados, $ estimado>
Acciones agotadas: <lista>
Decisión CTO requerida: <Failover región / Comunicación pública / Aceptar riesgo>
```

---

## 4. Autoridades de Decisión

| Decisión | Autoridad Mínima | Condición |
|---|---|---|
| **Rollback App** (kubectl rollout undo) | On-call (L1) | SEV-1/2, runbook indica |
| **Rollback Infra Stack** (cdk destroy) | Tech Lead (L2) | SEV-1, runbook indica, EM informado |
| **Failover Redis/Aurora Manual** | On-call (L1) | Runbook indica, automático preferido |
| **Scale Manual Pods/Nodos** | On-call (L1) | HPA insuficiente, SEV-1/2 |
| **Rollback Deploy Reciente** | On-call (L1) | Si deploy < 1h y correlaciona |
| **Comunicación Clientes Externos** | EM (L3) | SEV-1 > 30 min, impacto externo |
| **Status Page Public Update** | EM (L3) / CTO (L4) | SEV-1 > 30 min |
| **Accept Risk / Defer Fix** | CTO (L4) | SEV-1 > 60 min sin resolución |
| **Activate DR / Failover Región** | CTO (L4) | Región caída, RTO > 4h |
| **Post-Mortem Override** (no action items) | EM (L3) | Solo si riesgo aceptado documentado |

---

## 5. Comunicación Externa (Clientes/Partners)

### Trigger para Comunicación Externa
| Condición | Canal | Responsable | Plantilla |
|---|---|---|---|
| SEV-1 > 30 min sin resolución | Status Page + Email clientes afectados | EM | "Estamos investigando..." |
| SEV-1 resuelto | Status Page + Email | EM | "Resuelto: <resumen>" |
| Brecha datos (PII) | Email directo + Legal | CTO + Legal | Notificación GDPR/PCI (72h) |
| Mantenimiento planificado > 30 min impacto | Status Page + Email (48h antes) | EM | "Mantenimiento programado..." |

### Plantilla Status Page (Iniciando)
```
Título: Degradación del servicio de consulta de stock
Estado: Investigando
Inicio: 2026-10-18 14:32 UTC
Impacto: Consultas de stock pueden fallar o ser lentas
Causa: En investigación
Próxima actualización: 14:45 UTC
```

### Plantilla Status Page (Resuelto)
```
Título: Servicio de consulta de stock restaurado
Estado: Resuelto
Inicio: 2026-10-18 14:32 UTC
Fin: 2026-10-18 14:47 UTC
Duración: 15 min
Causa: Failover Redis ElastiCache (automático)
Impacto: Consultas de stock lentas/fallidas durante 15 min
Acción: Failover automático completado. Cache repoblándose.
Prevención: Revisión TTLs y alertas memoria Redis.
```

---

## 5. Integración con PagerDuty

### Escalation Policy `andes-incident-response`
| Level | Target | Delay | Actions |
|---|---|---|---|
| 1 | On-Call Primary (`andes-oncall`) | 0 min | Push, SMS, Call |
| 2 | On-Call Secondary (`andes-oncall-backup`) | 5 min | Push, SMS, Call |
| 3 | Tech Lead (`andes-tech-lead`) | 15 min | Push, SMS, Call |
| 4 | Engineering Manager | 30 min | Push, Email |
| 5 | CTO | 60 min | Call directo |

### Service Integrations
| Service | Integration | Severity Mapping |
|---|---|---|
| CloudWatch Alarms | EventBridge → PagerDuty | CRITICAL → P1, WARNING → P2 |
| Prometheus Alertmanager | Webhook → PagerDuty | critical → P1, warning → P2 |
| Slack `#andes-alerts` | PagerDuty Extension | Auto-ack on reaction ✅ |

---

## 6. Integración con AWS Incident Manager (Fase 2)

### Plan de Integración
1. Crear **Response Plan** en AWS Incident Manager para cada runbook crítico (RB-001 a RB-008)
2. Vincular **Engagements** automáticos desde CloudWatch Alarms → Incident Manager
3. Usar **Runbook Automation (SSM Automation)** para pasos automatizados (failover, rollback, scale)
4. **Timeline** automático en Incident Manager → export a post-mortem

### Runbooks Automatizados (SSM Automation) - Candidatos
| Runbook | Automatización | Parámetros |
|---|---|---|
| RB-001 (CB Open) | Verificar SAP health, reiniciar pods app | `cluster`, `namespace`, `deployment` |
| RB-006 (Redis Down) | Failover ElastiCache | `replication-group-id` |
| RB-007 (Aurora Down) | Failover Aurora, restart Keycloak | `db-cluster-id`, `keycloak-deployment` |
| RB-008 (Kong Down) | Restart Kong deployment | `namespace`, `deployment` |

---

## 6. Revisión y Actualización

| Revisión | Frecuencia | Responsable | Trigger |
|---|---|---|---|
| Contactos On-Call | Cada rotación (semanal) | EM | PagerDuty schedule update |
| Contactos L2-L4 | Trimestral | EM | Cambio org / role change |
| Runbook Links | Mensual | SRE | Post-mortem / Game Day |
| Severity Definitions | Semestral | Tech Lead + EM | Post-mortem major / Org change |
| Communication Templates | Anual | EM + Comms | Brand change / Compliance update |
| PagerDuty Policies | Trimestral | SRE | Team change / Tool change |