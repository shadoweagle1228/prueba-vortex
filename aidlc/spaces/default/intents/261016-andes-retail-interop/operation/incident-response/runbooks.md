# Incident Runbooks — `skeleton-stock-query`

## Referencias upstream

| Artefacto | Ubicación | Qué aporta |
|---|---|---|
| `alarms.md` | `operation/observability-setup/alarms.md` | Alarmas CRIT/WARN/INFO, runbook links |
| `reliability-design` | `construction/skeleton-stock-query/nfr-design/reliability-design.md` | CB/Bulkhead, degradación, fail-closed |
| `infrastructure-specification` | `construction/skeleton-stock-query/infrastructure-design/infrastructure-specification.md` | Specs EKS, Redis, Aurora, Kong, Keycloak |
| `deployment-log` | `operation/deployment-execution/deployment-log.md` | Procedimiento deploy, rollback |
| `rollback-runbook` | `operation/deployment-pipeline/rollback-runbook.md` | Rollback app/infra |
| `anomaly-config` | `operation/observability-setup/anomaly-config.md` | Detección anomalías, runbook respuesta |

---

## Índice de Runbooks

| Runbook ID | Título | Severidad | Desencadenante | Tiempo objetivo |
|---|---|---|---|---|
| RB-001 | Circuit Breaker SAP Abierto | CRITICAL | `CRIT-STOCK-CB-OPEN` | < 15 min |
| RB-002 | Bulkhead ERP Agotado | CRITICAL | `CRIT-STOCK-BULKHEAD-EXHAUSTED` | < 15 min |
| RB-003 | Latencia p95 Degradada | CRITICAL | `CRIT-STOCK-LATENCY-P95` | < 30 min |
| RB-004 | Error Rate 5xx Alto | CRITICAL | `CRIT-STOCK-ERROR-RATE` | < 30 min |
| RB-005 | Health Checks Fallando | CRITICAL | `CRIT-STOCK-AVAILABILITY` | < 15 min |
| RB-006 | Redis ElastiCache No Disponible | CRITICAL | `CRIT-REDIS-UNAVAILABLE` | < 30 min |
| RB-007 | Aurora PostgreSQL No Disponible | CRITICAL | `CRIT-AURORA-UNAVAILABLE` | < 30 min |
| RB-008 | Kong Ingress Down | CRITICAL | `CRIT-KONG-UNAVAILABLE` | < 15 min |
| RB-009 | Latencia p95 Drift (Warning) | WARNING | `WARN-STOCK-LATENCY-P95` / `WARN-ANOMALY-STOCK-LATENCY-DRIFT` | < 60 min |
| RB-010 | Cache Hit Ratio Bajo | WARNING | `WARN-STOCK-CACHE-HIT-RATIO` / `WARN-ANOMALY-CACHE-HIT-RATIO-DROP` | < 60 min |
| RB-011 | SAP Latencia Alta | WARNING | `WARN-STOCK-SAP-LATENCY` / `WARN-ANOMALY-STOCK-SAP-LATENCY` | < 60 min |
| RB-012 | Redis CPU/Memoria Alto | WARNING | `WARN-REDIS-CPU` / `WARN-REDIS-MEMORY` / `WARN-ANOMALY-REDIS-*` | < 60 min |
| RB-013 | Aurora CPU/Conexiones Alto | WARNING | `WARN-AURORA-CPU` / `WARN-AURORA-CONNECTIONS` / `WARN-ANOMALY-AURORA-*` | < 60 min |
| RB-014 | Circuit Breaker Flapping | WARNING | `WARN-ANOMALY-CB-FLAPPING` | < 30 min |
| RB-015 | Tráfico Anómalo (Spike/Drop) | WARNING | `WARN-ANOMALY-STOCK-TRAFFIC-*` | < 30 min |
| RB-016 | Rate Limiting Excedido | WARNING | `INFO-KONG-RATELIMIT` (sostenido) | < 30 min |
| RB-017 | Certificado Próximo a Expirar | WARNING | `WARN-CERT-EXPIRY` | < 24h |
| RB-018 | PII Leakage en Logs | CRITICAL | `WARN-STOCK-PII-LEAKAGE` | < 15 min |

**Estado**: escritos en este documento → RB-001…RB-008 y RB-018 (**los 9 de severidad CRITICAL**).
En roadmap → RB-009…RB-017 (9 de severidad WARNING), que se documentan junto con la
detección de anomalías en el mes 5 del plan de implementación (sección 8).

---

## RB-001: Circuit Breaker SAP Abierto

### Desencadenante
- Alarma: `CRIT-STOCK-CB-OPEN` (`resilience4j_circuitbreaker_state{sapStockCircuitBreaker} == 1` por 1 min)
- Logs: `CircuitBreaker 'sapStockCircuitBreaker' changed state from CLOSED to OPEN`

### Impacto
- App deja de llamar a SAP ERP
- Requests fallan con 503 `Retry-After: 5` (`type: urn:andes:error:stock-unavailable`)
- Degradación a `stale-cache` si existe en Redis

### Diagnóstico Rápido (< 5 min)
```bash
# 1. Verificar estado CB
curl -k -H "Authorization: Bearer $METRICS_TOKEN" https://api.../actuator/prometheus | grep circuitbreaker_state

# 2. Verificar logs recientes
kubectl logs -n andes-retail -l app=stock-price-adapter --tail=200 | grep -i circuitbreaker

# 3. Verificar conectividad SAP
kubectl exec -n andes-retail deploy/stock-price-adapter -- curl -v --max-time 5 $SAP_BASE_URL/health 2>&1 | head -20
```

### Causas Comunes
| Causa | Indicadores | Acción |
|---|---|---|
| SAP ERP caído / mantenimiento | CB open + logs SAP timeout/connection refused | Esperar recuperación SAP (CB auto-cierra en 60s half-open) |
| Red/VPN caída | Timeout conexión SAP, sin respuesta | Verificar VPN, Security Groups, NACLs |
| Latencia SAP > 250ms sostenida | Slow call rate > 50% | Verificar SAP side, considerar aumentar timeout temporalmente |
| Bug deploy reciente | CB open tras deploy | Rollback app (`kubectl rollout undo`) |

### Resolución
1. **Si SAP caído**: Esperar. CB auto-intenta half-open cada 60s. App sirve `stale-cache` mientras tanto.
2. **Si red/VPN**: Restaurar conectividad → CB auto-cierra en half-open exitoso.
3. **Si latencia alta**: Verificar SAP, considerar scale-up temporal o aumento timeout (configmap + rollout restart).
4. **Si bug deploy**: `kubectl -n andes-retail rollout undo deployment/stock-price-adapter`

### Verificación Post-Resolución
- CB state = 0 (CLOSED) en métricas
- Latencia p95 < 300ms
- 0 errores 5xx en 5 min
- `stale-cache` no servido (source = `cache` o `erp`)

---

## RB-002: Bulkhead ERP Agotado

### Desencadenante
- Alarma: `CRIT-STOCK-BULKHEAD-EXHAUSTED` (`available_concurrent_calls == 0` por 2 min)
- Logs: `Bulkhead 'sapStockBulkhead' rejected call`

### Impacto
- Requests rechazados antes de llamar a SAP (503 inmediato)
- No hay llamadas concurrentes > 20 a SAP
- Degradación a `stale-cache` o fail-closed

### Diagnóstico Rápido (< 5 min)
```bash
# 1. Verificar bulkhead state
curl -k -H "Authorization: Bearer $METRICS_TOKEN" https://api.../actuator/prometheus | grep bulkhead_available

# 2. Verificar tráfico actual
curl -k -H "Authorization: Bearer $METRICS_TOKEN" https://api.../actuator/prometheus | grep http_server_requests_seconds_count

# 3. Verificar latencia SAP (causa raíz común)
curl -k -H "Authorization: Bearer $METRICS_TOKEN" https://api.../actuator/prometheus | grep sap_client_latency
```

### Causas Comunes
| Causa | Indicadores | Acción |
|---|---|---|
| Spike tráfico legítimo | RPS ↑, bulkhead saturado, latencia normal | Scale HPA manual (`kubectl scale deploy --replicas=6`), revisar rate limiting Kong |
| Latencia SAP alta → requests se acumulan | SAP latency ↑, bulkhead llena, CB puede abrir después | Ver SAP side, considerar scale-up pods |
| Bug: requests no liberan bulkhead | Bulkhead llena, latencia normal, RPS normal | Rollback deploy, revisar código SapClient |

### Resolución
1. **Spike tráfico**: `kubectl -n andes-retail scale deploy stock-price-adapter --replicas=6` (max 10 por HPA)
2. **Latencia SAP**: Verificar SAP, CB protegerá si sostenido
3. **Bug**: `kubectl rollout undo deployment/stock-price-adapter`

### Verificación
- Bulkhead available > 5
- Latencia p95 < 300ms
- 0 rechazos bulkhead en 5 min

---

## RB-003: Latencia p95 Degradada

### Desencadenante
- Alarma: `CRIT-STOCK-LATENCY-P95` (p95 > 500ms por 5 min)
- O anomalía: `WARN-ANOMALY-STOCK-LATENCY-DRIFT` (p95 50% > baseline 1h)

### Impacto
- NFR-01 violado (p95 ≤ 300ms)
- UX degradada para canales
- Posible violación SLO-STOCK-LATENCY

### Diagnóstico Rápido (< 10 min)
```bash
# 1. Desglose latencia por componente
curl -k -H "Authorization: Bearer $METRICS_TOKEN" https://api.../actuator/prometheus | grep -E "http_server|sap_client|redis"

# 2. Verificar cache hit ratio
curl -k -H "Authorization: Bearer $METRICS_TOKEN" https://api.../actuator/prometheus | grep cache_hit

# 3. Verificar SAP latency
curl -k -H "Authorization: Bearer $METRICS_TOKEN" https://api.../actuator/prometheus | grep sap_client_latency

# 4. Verificar Redis latency
curl -k -H "Authorization: Bearer $METRICS_TOKEN" https://api.../actuator/prometheus | grep redis
```

### Árbol de Decisión
```
Latencia alta
├── ¿Cache hit ratio < 80%?
│   ├── SÍ → Redis issue (ver RB-012)
│   └── NO → ¿SAP latency > 250ms?
│       ├── SÍ → SAP issue (ver RB-011)
│       └── NO → ¿CPU/Mem pods altos?
│           ├── SÍ → Scale pods / revisar memory leaks
│           └── NO → Revisar GC, thread pool, DB connections
```

### Resolución Típica
| Causa | Acción |
|---|---|
| Redis caído/lento | Failover Redis, scale-up, revisar TTLs |
| SAP lento | CB protegerá, scale pods temporal |
| CPU pods alto | Scale HPA manual, revisar memory leaks |
| GC pauses | Aumentar heap, revisar allocation rate |

---

## RB-004: Error Rate 5xx Alto

### Desencadenante
- Alarma: `CRIT-STOCK-ERROR-RATE` (5xx > 5% por 3 min)
- Métrica: `rate(http_server_requests_seconds_count{status=~"5.."}[3m]) / rate(http_server_requests_seconds_count[3m]) > 0.05`

### Impacto
- Los canales reciben respuestas 5xx (503 fail-closed o 500 inesperado)
- Quema acelerada del error budget de `SLO-STOCK-AVAILABILITY` (99.9% → 43.2 min/mes)
- Si es un deploy reciente, afecta a todas las réplicas al mismo tiempo

### Diagnóstico Rápido (< 5 min)
```bash
# 1. ¿Es un deploy reciente?
kubectl -n andes-retail rollout history deployment/stock-price-adapter

# 2. ¿Son 503 diseñados (fail-closed) o 500 inesperados (bug)?
kubectl logs -n andes-retail -l app=stock-price-adapter --tail=500 | grep -oE "stock-unavailable|Internal Server Error" | sort | uniq -c

# 3. Estado de los mecanismos de protección
curl -k -H "Authorization: Bearer $METRICS_TOKEN" https://api.../actuator/prometheus | grep -E "circuitbreaker_state|bulkhead_available|stock_fail_closed"

# 4. Verificar respuesta completa (cuerpo y cabeceras)
curl -i -H "Authorization: Bearer $TOKEN" https://api.../v1/inventory/stock/SKU-TEST
```

### Causas Comunes
| Causa | Indicadores | Acción |
|---|---|---|
| ERP caído + sin stale-cache | CB OPEN + `stock_fail_closed_total` ↑ + Redis sin claves stale | Restaurar SAP (RB-001) |
| Redis caído | Connection refused, cache hit = 0 | Ver RB-006 |
| Bug en deploy reciente | 500 con stacktrace, rollout < 30 min | Rollback inmediato |
| Keycloak / JWKS inaccesible | 401 masivo (no son 5xx del servidor) | Verificar Keycloak, JWKS cache 15 min |
| Rate limiting por consumer | 429 en volumen | Revisar plugins Kong (RB-016) |

### Resolución
1. **Deploy reciente con causalidad clara** → `kubectl -n andes-retail rollout undo deployment/stock-price-adapter` (decidir en < 5 min; el análisis va después)
2. **SAP caído** → RB-001; la app sirve `stale-cache` mientras tanto
3. **Redis caído** → RB-006; la app degrada a consulta directa a SAP con CB activo
4. **Sin causa identificable** → escalar a L3 con la salida de los 4 comandos de diagnóstico

### Verificación Post-Resolución
- Error rate 5xx < 0.1% durante 5 min
- `stock_fail_closed_total` deja de incrementarse
- Burn rate de `SLO-STOCK-AVAILABILITY` vuelve a 1x

---

## RB-005: Health Checks Fallando

### Desencadenante
- Alarma: `CRIT-STOCK-AVAILABILITY` (health checks failing por 2 min)
- Kubernetes: pod `CrashLoopBackOff` o readiness probe fallando (>3 fallos seguidos)

### Impacto
- El pod sale del Service: no recibe tráfico
- Si todas las réplicas fallan → servicio caído (violación directa del 99.9%)
- Si 1 de 2 réplicas falla → capacidad a la mitad y el HPA puede no compensar a tiempo

### Diagnóstico Rápido (< 5 min)
```bash
# 1. Estado de los pods
kubectl -n andes-retail get pods -l app.kubernetes.io/name=stock-price-adapter

# 2. ¿Liveness (fuerza restart) o readiness (saca del Service)?
kubectl -n andes-retail describe pod <pod-fallido> | tail -30

# 3. Logs del último arranque
kubectl -n andes-retail logs <pod-fallido> --previous --tail=200

# 4. Verificar el grupo de readiness desde dentro del pod
kubectl -n andes-retail exec deploy/stock-price-adapter -- curl -s localhost:8080/actuator/health/readiness
```

### Causas Comunes
| Causa | Indicadores | Acción |
|---|---|---|
| OOMKill | `lastState.terminated.reason: OOMKilled`, restarts ↑ | Subir memory limit (1Gi → 1.5Gi) y revisar allocation rate |
| Arranque lento (JVM + JWKS) | Startup probe agotado (24 × 5s = 120s) | Aumentar `failureThreshold` del startup probe |
| Secret inaccesible | Logs: `secret "stock-price-adapter-secrets" not found` | Verificar External Secrets Operator |
| Imagen no desplegable | `ImagePullBackOff` | Verificar tag en ECR y política de pull |
| Redis en readiness | Redis caído → readiness falla | Ver RB-006; por diseño Redis **no** está en el grupo de readiness |

### Resolución
1. **OOMKilled** → `kubectl -n andes-retail set resources deploy/stock-price-adapter --limits=memory=1500Mi` (sin revisar heap antes, es parche)
2. **Arranque lento** → revisar tiempo de resolución JWKS y pool de conexiones
3. **Secret** → `kubectl -n andes-retail get externalsecret` y verificar sync con Secrets Manager
4. **Deploy reciente** → `kubectl -n andes-retail rollout undo deployment/stock-price-adapter`

### Verificación Post-Resolución
- 2/2 pods `Running` y `Ready`
- `kubectl -n andes-retail get endpoints stock-price-adapter` con 2 IPs
- `200` en `/actuator/health/readiness` y `/actuator/health/liveness`
- Sin restarts nuevos en 10 min

---

## RB-006: Redis ElastiCache No Disponible

### Desencadenante
- Alarma: `CRIT-REDIS-UNAVAILABLE` (ReplicationGroup status != available)
- App logs: `Redis connection failed`, `Cache miss` masivo

### Impacto
- Cache completamente inaccesible
- App degrada a consultar SAP directamente (sin cache)
- Latencia ↑ (sin cache), carga SAP ↑
- Circuit Breaker protege si SAP saturado

### Diagnóstico Rápido (< 5 min)
```bash
# 1. Estado Redis
aws elasticache describe-replication-groups --replication-group-id andes-retail-redis --query 'ReplicationGroups[0].Status'

# 2. Eventos recientes
aws elasticache describe-events --source-type replication-group --source-id andes-retail-redis --duration 60

# 3. Verificar failover automático
aws elasticache describe-replication-groups --replication-group-id andes-retail-redis --query 'ReplicationGroups[0].NodeGroups[0].NodeGroupMembers[].CurrentRole'
```

### Resolución
1. **Failover automático**: ElastiCache Multi-AZ hace failover en < 60s. Verificar `CurrentRole` cambiado.
2. **Si no hay failover**: `aws elasticache failover-replication-group --replication-group-id andes-retail-redis`
3. **Si cluster corrupto**: Restaurar desde snapshot más reciente (`aws elasticache create-replication-group --replication-group-id ... --snapshot-name ...`)

### Verificación Post-Recuperación
- `aws elasticache describe-replication-groups` → `Status: available`
- App logs: `Redis connection restored`, cache hits regresan
- Cache hit ratio > 80% en 5 min

---

## RB-007: Aurora PostgreSQL No Disponible

### Desencadenante
- Alarma: `CRIT-AURORA-UNAVAILABLE` (DBCluster status != available)
- Keycloak logs: `Connection refused`, `FATAL: remaining connection slots reserved`

### Impacto
- Keycloak no puede autenticar (BD down)
- Todos los requests 401/500
- App stock-price-adapter no afectado directamente (no usa Aurora)

### Diagnóstico Rápido (< 5 min)
```bash
# 1. Estado Aurora
aws rds describe-db-clusters --db-cluster-identifier andes-retail-aurora --query 'DBClusters[0].Status'

# 2. Verificar writer/reader
aws rds describe-db-clusters --db-cluster-identifier andes-retail-aurora --query 'DBClusters[0].DBClusterMembers[].{Role:IsClusterWriter,Status:DBInstanceStatus}'

# 3. Eventos
aws rds describe-events --source-type db-cluster --source-id andes-retail-aurora --duration 60
```

### Resolución
1. **Failover automático**: Aurora Serverless v2 hace failover a reader en < 30s.
2. **Si writer caído**: `aws rds failover-db-cluster --db-cluster-identifier andes-retail-aurora`
3. **Reiniciar Keycloak pods** tras recuperación: `kubectl -n keycloak rollout restart deployment/keycloak`

### Verificación
- Aurora status = `available`
- Keycloak pods `Running` y `Ready`
- Token Keycloak válido → `GET /v1/inventory/stock/SMOKE` = 200

---

## RB-008: Kong Ingress Down

### Desencadenante
- Alarma: `CRIT-KONG-UNAVAILABLE` (Kong pods not ready, ALB health check failing)
- ALB 5xx / 502/503 responses

### Impacto
- Todo tráfico externo bloqueado (502/503 en ALB)
- Ningún request llega a app
- Canales completamente caídos

### Diagnóstico Rápido (< 5 min)
```bash
# 1. Estado pods Kong
kubectl -n kong get pods -l app=kong

# 2. ALB target health
aws elbv2 describe-target-health --target-group-arn <kong-tg-arn>

# 3. Logs Kong
kubectl -n kong logs -l app=kong --tail=100
```

### Resolución
1. **Pods caídos**: `kubectl -n kong rollout restart deployment/kong`
2. **Config error**: Revisar ConfigMap/Secret Kong, `kubectl rollout restart`
3. **ALB issue**: Verificar Security Groups, Target Group health checks
4. **Rollback Kong**: `helm rollback kong -n kong` si deploy reciente

### Verificación
- Kong pods `Running`/`Ready`
- ALB Target Health = `healthy`
- `curl -k https://api.andes-retail.example/actuator/health/liveness` = 200

---

## RB-018: PII Leakage en Logs

### Desencadenante
- Alarma: `WARN-STOCK-PII-LEAKAGE` (detección regex PII en logs)
- Query Logs Insights detecta: password, token, secret, credit, card, pan, ssn, dni

### Impacto
- Violación PCI-DSS / GDPR
- Fuga de datos sensibles en CloudWatch Logs
- Riesgo legal y reputacional

### Respuesta Inmediata (< 15 min)
```bash
# 1. Identificar logs afectados
aws logs filter-log-events --log-group-name /aws/eks/andes-retail-staging/containers --filter-pattern "[password, token, secret, credit, card, pan, ssn, dni]" --start-time $(date -d '1 hour ago' +%s)000

# 2. Identificar pod/deployment origen
kubectl logs -n andes-retail -l app=stock-price-adapter --tail=500 | grep -iE 'password|token|secret|credit|card|pan'

# 3. Detener leakage (si es bug en código)
kubectl -n andes-retail rollout restart deployment/stock-price-adapter
```

### Investigación
1. Identificar código que loggea PII (¿`log.info("token: {}", token)`?)
2. Fix: Sanitizar logs (`log.info("token: ***")` o no loggear)
3. Deploy fix: `./gradlew bootJar && docker build... && kubectl set image...`

### Limpieza Post-Fix
```bash
# Eliminar logs contaminados (requiere permisos)
aws logs delete-log-group --log-group-name /aws/eks/andes-retail-staging/containers  # CUIDADO: elimina TODO
# O filtrar y exportar logs limpios a nuevo log group
```

### Prevención
- Code review: prohibir loggear `password`, `token`, `secret`, `creditCard`, `pan`
- Log sanitization automática (Logback pattern masking)
- SAST en CI: SpotBugs + FindSecBugs detecta logging de passwords

---

## Comunicación Durante Incidentes

| Canal | Uso | Audiencia |
|---|---|---|
| `#andes-alerts` (Slack) | Alertas automáticas, estado incidente | On-call, Tech Lead, SRE |
| `#andes-incidents` (Slack) | Coordinación durante incidente activo | On-call, Tech Lead, SRE, EM |
| `#andes-status` (Slack) | Status page updates | Todo equipo, stakeholders |
| PagerDuty | Escalación CRITICAL | On-call rotativo |
| Status Page | Comunicación externa | Clientes, partners |

### Plantilla Inicio Incidente
```
🚨 INCIDENTE [SEV-1/SEV-2] - <Título>
Servicio: stock-price-adapter
Entorno: staging/prod
Inicio: <timestamp>
Trigger: <alarma/métrica>
Impacto: <usuarios afectados, funcionalidad>
Estado: Investigando / Mitigando / Resuelto
Lead: @oncall
```

### Plantilla Resolución
```
✅ INCIDENTE RESUELTO - <Título>
Servicio: stock-price-adapter
Entorno: staging/prod
Inicio: <timestamp>
Fin: <timestamp>
Duración: <X min>
Causa raíz: <resumen>
Acción: <qué se hizo>
Prevención: <qué se hará>
Post-mortem: <link ticket>
```

---

## Escalation Matrix

| Nivel | Tiempo sin respuesta | Acción | Contacto |
|---|---|---|---|
| L1 (On-call) | 0 min | Recibe alerta, empieza investigación | On-call rotativo |
| L2 (Tech Lead) | 15 min sin respuesta L1 | Se une a investigación, decide rollback | Tech Lead |
| L3 (Engineering Manager) | 30 min sin resolución | Autoriza rollback mayor, comunica stakeholders | EM |
| L4 (CTO) | 60 min sin resolución / impacto masivo | Decisión estratégica, comunicación externa | CTO |

### Contactos (Placeholders)
| Rol | Nombre | Slack | PagerDuty | Teléfono |
|---|---|---|---|---|
| On-call (rotativo) | — | `@oncall` | `andes-oncall` | — |
| Tech Lead | — | `@tech-lead` | `andes-tech-lead` | — |
| SRE Lead | — | `@sre-lead` | `andes-sre` | — |
| Engineering Manager | — | `@eng-manager` | — | — |
| CTO | — | `@cto` | — | — |

---

## Post-Mortem Process

### Timeline
1. **Inmediato**: Resolución + comunicación
2. **24h**: Ticket post-mortem creado (template abajo)
3. **72h**: Post-mortem completado, revisado por Tech Lead
4. **1 semana**: Action items en sprint planning

### Post-Mortem Template
```
# Post-Mortem: <Título Incidente>

## Resumen
- **Incidente**: #INC-XXXX
- **Fecha**: YYYY-MM-DD
- **Duración**: XX min
- **Severidad**: SEV-1/SEV-2
- **Servicios afectados**: stock-price-adapter, Kong, Keycloak, etc.

## Timeline
- HH:MM: Inicio (trigger)
- HH:MM: Detección (alarma)
- HH:MM: On-call ack
- HH:MM: Diagnóstico causa raíz
- HH:MM: Mitigación aplicada
- HH:MM: Resuelto
- HH:MM: Post-incidente comms

## Causa Raíz (5 Whys)
1. ¿Qué pasó? ...
2. ¿Por qué pasó? ...
3. ¿Por qué pasó eso? ...
4. ¿Por qué pasó eso? ...
5. ¿Por qué pasó eso? → **Causa raíz**

## Impacto
- Usuarios afectados: XX
- Requests fallidos: XX%
- Duración degradación: XX min
- Pérdida estimada: $XX

## Acción Inmediata (Mitigación)
- Qué se hizo para restaurar servicio

## Causa Raíz
- Descripción técnica detallada

## Acciones Correctivas (Action Items)
| Acción | Responsable | Fecha límite | Ticket |
|---|---|---|---|
| Fix código / config | @dev | YYYY-MM-DD | JIRA-XXXX |
| Mejora monitor/alerta | @sre | YYYY-MM-DD | JIRA-XXXX |
| Runbook update | @sre | YYYY-MM-DD | JIRA-XXXX |
| Chaos experiment | @sre | YYYY-MM-DD | JIRA-XXXX |

## Lecciones Aprendidas
- Qué funcionó bien
- Qué no funcionó
- Qué cambiaríamos
```