# Rollback Runbook — `skeleton-stock-query`

## Principios

1. **Rollback < 5 min** para aplicación (Kubernetes rollout undo)
2. **Rollback < 15 min** para infraestructura (CDK destroy/redeploy stack)
3. **Datos**: Aurora y Redis son multi-AZ con backups automáticos — no se revierten salvo catástrofe
4. **Comunicación**: Slack `#andes-alerts` + GitHub issue automático

---

## 1. Rollback de aplicación (Kubernetes)

### Cuándo
- Error rate 5xx > 5% tras deploy
- Latencia p95 > 1s sostenida
- Circuit Breaker abierto persistentemente
- Health checks fallando

### Procedimiento (≤ 3 min)

```bash
# 1. Verificar estado actual
kubectl -n andes-retail get deployment stock-price-adapter
kubectl -n andes-retail rollout history deployment/stock-price-adapter

# 2. Rollback inmediato a revisión anterior
kubectl -n andes-retail rollout undo deployment/stock-price-adapter

# 3. Verificar rollback
kubectl -n andes-retail rollout status deployment/stock-price-adapter --timeout=5m

# 4. Validar salud
curl -s -o /dev/null -w "%{http_code}" \
  -H "Authorization: Bearer $TOKEN" \
  https://api.andes-retail.example/v1/inventory/stock/SMOKE-TEST
# Debe ser 200
```

### Rollback a revisión específica

```bash
# Ver historial
kubectl -n andes-retail rollout history deployment/stock-price-adapter

# Rollback a revisión N
kubectl -n andes-retail rollout undo deployment/stock-price-adapter --to-revision=N
```

### Verificación post-rollback
- [ ] Pods `Running` y `Ready` (`kubectl get pods -n andes-retail`)
- [ ] `/actuator/health/liveness` y `/readiness` = 200
- [ ] Smoke test endpoint stock = 200
- [ ] Métricas Prometheus scrapeando (`resilience4j_circuitbreaker_state = 0` CLOSED)
- [ ] Logs sin errores críticos (`kubectl logs -n andes-retail -l app=stock-price-adapter --tail=100`)

---

## 2. Rollback de configuración (ConfigMap/Secret)

### Cuándo
- Valores incorrectos en `stock-price-adapter-config` o secrets
- JWKS URI / Issuer URI erróneos
- Timeouts / pool sizes incorrectos

### Procedimiento

```bash
# 1. Ver valores actuales
kubectl -n andes-retail get configmap stock-price-adapter-config -o yaml
kubectl -n andes-retail get secret stock-price-adapter-secrets -o yaml

# 2. Restaurar desde git (valores conocidos buenos)
kubectl -n andes-retail apply -f deploy/k8s/configmap.yaml
kubectl -n andes-retail apply -f deploy/k8s/secret.yaml  # si existe

# 3. Forzar reload pods (rolling restart)
kubectl -n andes-retail rollout restart deployment/stock-price-adapter

# 4. Verificar
kubectl -n andes-retail rollout status deployment/stock-price-adapter
```

---

## 3. Rollback de infraestructura (CDK)

### ⚠️ ADVERTENCIA
Rollback de stacks CDK **destruye recursos** (Redis, Aurora, EKS). Solo usar en catástrofe. Preferir fix forward.

### Cuándo (extremo)
- Stack corrupto irrecuperable
- Región AWS caída (failover a otra región — plan DR separado)
- Cambio destructivo irreversible (ej. `removalPolicy: DESTROY` ejecutado)

### Procedimiento por stack (orden inverso)

```bash
# Perfil staging
export AWS_PROFILE=andes-staging
cd infra

# 1. Compute (EKS, Kong, Keycloak) - MÁS DESTRUCTIVO
#    Destruye cluster EKS, nodos, ALB, Kong, Keycloak
cdk destroy AndesRetailComputeStack --force

# 2. Data (Redis, Aurora) - DESTRUCTIVO (pérdida datos si no hay snapshot)
#    ¡Solo si no hay snapshot reciente!
cdk destroy AndesRetailDataStack --force

# 3. Security (KMS, Secrets) - MENOS DESTRUCTIVO
cdk destroy AndesRetailSecurityStack --force

# 4. Network (VPC, SGs) - BASE
cdk destroy AndesRetailNetworkStack --force
```

### Re-deploy tras rollback infra

```bash
# Orden correcto: Network → Security → Data → Compute
cdk deploy AndesRetailNetworkStack
cdk deploy AndesRetailSecurityStack
cdk deploy AndesRetailDataStack    # ¡Restaurar snapshot Aurora/Redis si existe!
cdk deploy AndesRetailComputeStack

# Post-deploy: re-deploy app
kubectl -n andes-retail apply -f deploy/k8s/
```

### Snapshots de recuperación (Data Stack)

| Recurso | Backup automático | Retención | Restauración |
|---|---|---|---|
| **Aurora PostgreSQL** | Snapshots diarios + point-in-time | 30 días | `aws rds restore-db-cluster-from-snapshot` |
| **ElastiCache Redis** | Snapshots diarios | 30 días | `aws elasticache create-replication-group --replication-group-id ... --snapshot-name ...` |

```bash
# Listar snapshots Aurora
aws rds describe-db-cluster-snapshots --db-cluster-identifier andes-retail-aurora-staging

# Restaurar Aurora (ejemplo)
aws rds restore-db-cluster-from-snapshot \
  --db-cluster-identifier andes-retail-aurora-staging-restored \
  --snapshot-identifier rds:andes-retail-aurora-staging-2026-10-18-03-00 \
  --engine aurora-postgresql \
  --engine-version 16.1
```

---

## 4. Rollback de red / seguridad (raro)

### Security Groups / NACLs
```bash
# Ver SG actual
aws ec2 describe-security-groups --group-ids sg-xxx

# Revertir a versión anterior (si usa SG gestionados por CDK)
cdk deploy AndesRetailNetworkStack  # Recrea SGs desde código
```

### KMS Key (rotación / programada para eliminación)
```bash
# Si key programada para eliminación (7-30 días)
aws kms cancel-key-deletion --key-id <key-id>

# Rotación manual (si política falló)
aws kms enable-key-rotation --key-id <key-id>
```

---

## 5. Comunicación y escalado

| Nivel | Trigger | Acción | Responsable |
|---|---|---|---|
| **N1** | Rollback app exitoso | Post en `#andes-alerts` + GitHub issue auto | On-call |
| **N2** | Rollback app falla / infra | PagerDuty → Tech Lead + SRE | Tech Lead |
| **N3** | Pérdida datos / región caída | Crisis call + DR plan | CTO + SRE Lead |

### Plantilla mensaje Slack
```
🔴 ROLLBACK EJECUTADO
Servicio: stock-price-adapter
Entorno: staging/prod
Trigger: [error rate / latency / health check]
Acción: kubectl rollout undo / cdk destroy
Estado: ✅ Verificado / ❌ Falló (escalar N2)
Ejecutor: @usuario
Timestamp: 2026-10-18TXX:XX:XXZ
```

---

## 6. Prevención (fix forward > rollback)

| Práctica | Implementación |
|---|---|
| **Tests en staging** | Smoke tests obligatorios post-deploy staging |
| **Canary** | Futuro: Argo Rollouts para 10% → 100% |
| **Feature flags** | Desactivar funcionalidad sin redeploy |
| **Immutable tags** | Imagen Docker `vX.Y.Z` + `latest` solo staging |
| **DB migrations** | Solo additive, backward-compatible (Flyway/Liquibase futuro) |

---

## Contactos de emergencia

| Rol | Nombre | Slack | PagerDuty |
|---|---|---|---|
| Tech Lead | — | `@tech-lead` | `andes-tech-lead` |
| SRE Lead | — | `@sre-lead` | `andes-sre` |
| On-call (rotativo) | — | `@oncall` | `andes-oncall` |