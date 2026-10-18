# Alarms — `skeleton-stock-query`

## Referencias upstream

| Artefacto | Ubicación | Qué aporta |
|---|---|---|
| `monitoring-design` | `construction/skeleton-stock-query/nfr-design/monitoring-design.md` | SLOs, golden signals, escalation |
| `reliability-design` | `construction/skeleton-stock-query/nfr-design/reliability-design.md` | CB/Bulkhead thresholds |
| `performance-design` | `construction/skeleton-stock-query/nfr-design/performance-design.md` | Latencia budgets |
| `dashboards.md` | `operation/observability-setup/dashboards.md` | Métricas base para alarmas |

---

## Convenciones de alarmas

| Severidad | Prefijo | SNS Topic | Escalación | Respuesta objetivo |
|---|---|---|---|---|
| **CRITICAL** | `CRIT-` | `andes-critical` | PagerDuty → On-call (inmediato) | < 5 min |
| **WARNING** | `WARN-` | `andes-warning` | Slack `#andes-alerts` → On-call (15 min) | < 30 min |
| **INFO** | `INFO-` | `andes-info` | Slack `#andes-ops` | Next business day |

**Naming**: `{PREFIX}{COMPONENT}-{METRIC}-{CONDITION}`
Ej: `CRIT-STOCK-CB-OPEN`, `WARN-REDIS-CPU-HIGH`

---

## Alarmas Críticas (CRIT-)

| Alarm ID | Nombre | Métrica (PromQL/CloudWatch) | Condición | Severidad | Acción runbook |
|---|---|---|---|---|---|
| `CRIT-STOCK-CB-OPEN` | Circuit Breaker SAP Abierto | `resilience4j_circuitbreaker_state{name="sapStockCircuitBreaker"} == 1` | Durante 1 min | CRITICAL | 1. Verificar SAP ERP disponibilidad<br>2. Revisar logs `stock-price-adapter`<br>3. Si SAP caído: esperar recuperación, CB auto-cierra en 60s<br>4. Si falso positivo: revisar timeouts/thresholds |
| `CRIT-STOCK-BULKHEAD-EXHAUSTED` | Bulkhead ERP Agotado | `resilience4j_bulkhead_available_concurrent_calls{name="sapStockBulkhead"} == 0` | Durante 2 min | CRITICAL | 1. Verificar latencia SAP (debe ser < 250ms)<br>2. Verificar si hay spike tráfico<br>3. Considerar scale-up HPA manual<br>4. Revisar `max-concurrent-calls: 20` |
| `CRIT-STOCK-LATENCY-P95` | Latencia p95 > 500ms | `histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/v1/inventory/stock/**"}[5m])) > 0.5` | Durante 5 min | CRITICAL | 1. Verificar dashboard Golden Signals<br>2. Revisar CB state, Redis, SAP latency<br>3. Scale-up pods si CPU alta<br>4. Si Redis caído: verificar ElastiCache |
| `CRIT-STOCK-ERROR-RATE` | Error Rate 5xx > 5% | `rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.05` | Durante 3 min | CRITICAL | 1. Verificar logs `stock-price-adapter`<br>2. Revisar CB state, SAP health<br>3. Rollback si deploy reciente |
| `CRIT-STOCK-AVAILABILITY` | Health checks fallando | `up{job="stock-price-adapter"} == 0` O `health_liveness != 1` | Durante 2 min | CRITICAL | 1. Verificar pods `Running`/`Ready`<br>2. Revisar eventos K8s<br>4. Rollback deployment si necesario |
| `CRIT-EKS-CLUSTER` | EKS Cluster Unhealthy | `aws_eks_cluster_status != "ACTIVE"` | Inmediato | CRITICAL | 1. Verificar AWS Health Dashboard<br>2. Contactar AWS Support si necesario |
| `CRIT-REDIS-UNAVAILABLE` | ElastiCache Down | `aws_elasticache_ReplicationGroup_Status != "available"` | Inmediato | CRITICAL | 1. Verificar eventos ElastiCache<br>2. Failover manual si primary caído<br>3. App degrada a ERP (CB protege) |
| `CRIT-AURORA-UNAVAILABLE` | Aurora Down | `aws_rds_DBCluster_Status != "available"` | Inmediato | CRITICAL | 1. Verificar eventos RDS<br>2. Failover a reader si writer caído<br>3. Keycloak impactado (reinicio pods) |
| `CRIT-KONG-UNAVAILABLE` | Kong Ingress Down | `up{job="kong"} == 0` | Durante 2 min | CRITICAL | 1. Verificar pods Kong<br>2. Revisar ALB health checks<br>3. Rollback Kong si deploy reciente |

---

## Alarmas de Advertencia (WARN-)

| Alarm ID | Nombre | Métrica | Condición | Severidad | Acción runbook |
|---|---|---|---|---|---|
| `WARN-STOCK-LATENCY-P95` | Latencia p95 > 300ms | `histogram_quantile(0.95, ...) > 0.3` | 5 min | WARNING | 1. Verificar dashboard<br>2. Revisar cache hit ratio, SAP latency<br>3. Prevenir escalada a CRIT |
| `WARN-STOCK-CACHE-HIT-RATIO` | Cache Hit Ratio < 80% | `cache_hit_ratio < 0.8` | 10 min | WARNING | 1. Verificar TTL fresco (60s)<br>2. Revisar Redis memoria/CPU<br>3. Verificar invalidaciones inesperadas |
| `WARN-STOCK-CACHE-HIT-RATIO-LOW` | Cache Hit Ratio < 50% | `cache_hit_ratio < 0.5` | 5 min | WARNING | 1. Acción inmediata: revisar Redis<br>2. Verificar keys `stock:fresh:*` existen<br>3. Posible Redis issue |
| `WARN-STOCK-SAP-LATENCY` | SAP Latencia > 250ms | `histogram_quantile(0.95, sap_client_latency) > 0.25` | 5 min | WARNING | 1. Verificar red VPN/on-prem<br>2. Revisar SAP side<br>3. CB protege si sostenido |
| `WARN-REDIS-CPU` | Redis CPU > 80% | `aws_elasticache_CPUUtilization > 80` | 10 min | WARNING | 1. Verificar comandos costosos (KEYS, FLUSH)<br>2. Considerar scale-up node type |
| `WARN-REDIS-MEMORY` | Redis Memoria < 20% libre | `aws_elasticache_FreeableMemory < 0.2 * max` | 15 min | WARNING | 1. Revisar TTLs, key count<br>2. Considerar scale-up o limpieza |
| `WARN-AURORA-CPU` | Aurora CPU > 80% | `aws_rds_CPUUtilization > 80` | 10 min | WARNING | 1. Revisar queries lentas (pg_stat_statements)<br>2. Verificar Keycloak conexiones |
| `WARN-AURORA-CONNECTIONS` | Aurora Conexiones > 80% max | `aws_rds_DatabaseConnections > 0.8 * max` | 10 min | WARNING | 1. Revisar pool Keycloak/ESO<br>2. Considerar max_connections parameter |
| `WARN-NODE-CPU` | EKS Node CPU > 80% | `node_cpu_usage > 80%` | 10 min | WARNING | 1. HPA debería escalar<br>2. Verificar límites requests/limits pods |
| `WARN-NODE-MEMORY` | Node Memoria < 20% libre | `node_memory_MemAvailable < 0.2 * total` | 10 min | WARNING | 1. Verificar memory leaks<br>2. HPA scale-up |
| `WARN-POD-RESTARTS` | Pod Restarts > 5/15min | `increase(kube_pod_container_status_restarts_total[15m]) > 5` | 15 min | WARNING | 1. Verificar OOMKilled, CrashLoopBackOff<br>2. Revisar límites memoria |
| `WARN-CERT-EXPIRY` | Certificado < 30 días | `aws_acm_certificate_expiry_days < 30` | Diario | WARNING | 1. Renovar certificado ACM<br>2. Validar DNS validation |
| `WARN-KMS-ROTATION` | KMS Sin Rotación | `aws_kms_key_rotation_status == false` | Diario | WARNING | 1. Habilitar rotación anual |
| `WARN-SECRETS-AGE` | Secret > 90 días sin rotar | `secretsmanager_secret_age_days > 90` | Semanal | WARNING | 1. Rotar secret SAP/Keycloak |

---

## Alarmas Informativas (INFO-)

| Alarm ID | Nombre | Métrica | Condición | Severidad | Uso |
|---|---|---|---|---|---|
| `INFO-STOCK-DEPLOY` | Deploy completado | Evento GitHub Actions / CDK | Success/Failure | INFO | Tracking deployments |
| `INFO-STOCK-ROLLOUT` | Rollout completado | `kubectl rollout status` | Success | INFO | Tracking rollouts |
| `INFO-HPA-SCALE` | HPA Escalado | `kube_hpa_status_desired_replicas` change | Change | INFO | Capacity tracking |
| `INFO-CB-STATE-CHANGE` | CB Cambio Estado | `resilience4j_circuitbreaker_state` change | Any | INFO | Debugging CB |

---

## Configuración CloudWatch Alarms (ejemplo)

```yaml
# CRIT-STOCK-CB-OPEN
Type: AWS::CloudWatch::Alarm
Properties:
  AlarmName: CRIT-STOCK-CB-OPEN
  AlarmDescription: "Circuit Breaker SAP abierto - ERP inalcanzable"
  MetricName: circuitbreaker_state
  Namespace: Prometheus
  Statistic: Maximum
  Period: 60
  EvaluationPeriods: 1
  Threshold: 1
  ComparisonOperator: GreaterThanOrEqualToThreshold
  TreatMissingData: notBreaching
  AlarmActions:
    - arn:aws:sns:us-east-1:111122223333:andes-critical
  OKActions:
    - arn:aws:sns:us-east-1:111122223333:andes-critical
  Dimensions:
    - Name: name
      Value: sapStockCircuitBreaker
```

---

## Runbook Links

| Alarm ID | Runbook |
|---|---|
| CRIT-STOCK-* | `rollback-runbook.md` + `deployment-log.md` |
| CRIT-EKS-* | AWS EKS Troubleshooting Guide |
| CRIT-REDIS-* | ElastiCache Failover Guide |
| CRIT-AURORA-* | Aurora Failover + Keycloak Restart Guide |
| WARN-REDIS-* | ElastiCache Performance Tuning |
| WARN-AURORA-* | Aurora Performance Insights |

---

## SNS Topics (a crear)

| Topic | ARN (placeholder) | Suscriptores |
|---|---|---|
| `andes-critical` | `arn:aws:sns:us-east-1:111122223333:andes-critical` | PagerDuty integration, Slack `#andes-alerts` |
| `andes-warning` | `arn:aws:sns:us-east-1:111122223333:andes-warning` | Slack `#andes-alerts`, Email on-call |
| `andes-info` | `arn:aws:sns:us-east-1:111122223333:andes-info` | Slack `#andes-ops` |
| `andes-ops` | `arn:aws:sns:us-east-1:111122223333:andes-ops` | Slack `#andes-ops` |