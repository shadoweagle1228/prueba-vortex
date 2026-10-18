# Deployment Execution Log — `skeleton-stock-query`

## Referencias upstream

| Artefacto | Ubicación | Qué aporta |
|---|---|---|
| `cd-config` | `operation/deployment-pipeline/cd-config.md` | Pipeline CD, cuentas AWS, stacks CDK, promotion gates |
| `deployment-strategy` | `operation/deployment-pipeline/deployment-strategy.md` | Rolling Update, canary opcional, feature flags, promotion matrix |
| `environment-inventory` | `operation/environment-provisioning/environment-inventory.md` | Recursos AWS/K8s a desplegar, checklists validación |
| `build-test-results` | `construction/build-and-test/test-results.md` | 58 tests, 95.3% cobertura, artefactos build |

## Resumen de ejecución

| Campo | Valor |
|---|---|
| **Entorno** | staging (pendiente credenciales AWS) |
| **Fecha planificada** | Por definir |
| **Ejecutor** | Pipeline CD (GitHub Actions) / Manual `cdk deploy` |
| **Versión app** | `0.1.0` (tag `v0.1.0` pendiente) |
| **Imagen Docker** | `ghcr.io/andes-retail/stock-price-adapter:v0.1.0` (linux/arm64) |
| **Estrategia** | Rolling Update (`maxUnavailable: 0`, `maxSurge: 1`) |

> **Nota**: Este log documenta el procedimiento de ejecución. La ejecución real requiere credenciales AWS y cuenta destino configurada.

---

## Pre-deployment Checks

| Check | Comando | Estado esperado | Resultado |
|---|---|---|---|
| CI pipeline verde | GitHub Actions `CI` workflow | ✅ Pass | ✅ (local) |
| Imagen Docker construida | `docker buildx build --platform linux/arm64` | ✅ Build ok | ✅ (local) |
| CDK synth | `py -3 infra/app.py` | EXIT 0, `cdk.out/` generado | ✅ (local) |
| Cuenta AWS accesible | `aws sts get-caller-identity --profile andes-staging` | Account ID correcto | ⏳ Pendiente |
| EKS cluster accesible | `kubectl --context arn:aws:eks:us-east-1:111122223333:cluster/andes-retail-staging get nodes` | Nodes Ready | ⏳ Pendiente |
| Kong/Keycloak Healthy | `kubectl -n kong get pods`, `kubectl -n keycloak get pods` | All Running/Ready | ⏳ Pendiente |
| External Secrets sincronizados | `kubectl -n andes-retail get externalsecret` | `SecretSynced: True` | ⏳ Pendiente |
| ConfigMap/Secrets aplicados | `kubectl -n andes-retail get cm,secret` | Valores correctos | ⏳ Pendiente |
| Ventana de deploy | Horario bajo tráfico | Definido | ⏳ Pendiente |

---

## Pasos de ejecución (procedimiento)

### 1. Deploy Infraestructura CDK (staging)

```bash
# Perfil staging
export AWS_PROFILE=andes-staging
cd infra

# Instalar dependencias
pip install -r requirements.txt

# Deploy ordenado (CDK resuelve dependencias)
cdk deploy AndesRetailNetworkStack --require-approval never
cdk deploy AndesRetailSecurityStack --require-approval never
cdk deploy AndesRetailDataStack --require-approval never
cdk deploy AndesRetailComputeStack --require-approval never

# Verificar stacks
aws cloudformation list-stacks --stack-status-filter CREATE_COMPLETE UPDATE_COMPLETE \
  --query "StackSummaries[?starts_with(StackName, 'AndesRetail')].{Name:StackName,Status:StackStatus}"
```

**Tiempo estimado**: 25-35 min
**Rollback si fallo**: `cdk destroy <stack-fallido> --force` (orden inverso)

### 2. Deploy Manifiestos Kubernetes

```bash
# Contexto kubectl
kubectl config use-context arn:aws:eks:us-east-1:111122223333:cluster/andes-retail-staging

# Namespace y RBAC
kubectl apply -f deploy/k8s/namespace.yaml
kubectl apply -f deploy/k8s/serviceaccount.yaml

# Config y Secrets
kubectl apply -f deploy/k8s/configmap.yaml
# Secrets via ExternalSecrets (automático tras ESO deploy)

# App deployment
kubectl apply -f deploy/k8s/deployment.yaml
kubectl apply -f deploy/k8s/service.yaml
kubectl apply -f deploy/k8s/hpa.yaml
kubectl apply -f deploy/k8s/networkpolicy.yaml

# Kong + Keycloak + Monitoring
kubectl apply -k deploy/kong/
kubectl apply -k deploy/k8s/monitoring/

# Verificar
kubectl -n andes-retail get all
kubectl -n andes-retail get netpol,servicemonitor,pdb
kubectl -n kong get all,ingress,kongplugin,kongconsumer
kubectl -n external-secrets get externalsecret
```

**Tiempo estimado**: 5-10 min

### 3. Rollout App (Rolling Update)

```bash
# Trigger rollout (si imagen nueva)
kubectl -n andes-retail set image deployment/stock-price-adapter \
  stock-price-adapter=ghcr.io/andes-retail/stock-price-adapter:v0.1.0

# O simplemente restart si config cambió
kubectl -n andes-retail rollout restart deployment/stock-price-adapter

# Verificar rollout
kubectl -n andes-retail rollout status deployment/stock-price-adapter --timeout=10m

# Verificar pods
kubectl -n andes-retail get pods -l app.kubernetes.io/name=stock-price-adapter -w
```

**Tiempo estimado**: 3-5 min (2 pods, maxSurge 1)

### 4. Verificación Post-Deploy (Smoke Tests)

```bash
# 1. Health checks
curl -k -s -o /dev/null -w "%{http_code}" https://api.andes-retail.example/actuator/health/liveness
curl -k -s -o /dev/null -w "%{http_code}" https://api.andes-retail.example/actuator/health/readiness

# 2. Token Keycloak
TOKEN=$(curl -s -X POST https://auth.andes-retail.example/realms/andes-retail/protocol/openid-connect/token \
  -d "grant_type=client_credentials&client_id=canal-ecommerce&client_secret=..." | jq -r .access_token)

# 3. Endpoint stock
curl -k -s -H "Authorization: Bearer $TOKEN" \
  https://api.andes-retail.example/v1/inventory/stock/SMOKE-TEST

# 4. Rate limiting
for i in {1..101}; do
  curl -k -s -o /dev/null -w "%{http_code}\n" \
    -H "Authorization: Bearer $TOKEN" \
    https://api.andes-retail.example/v1/inventory/stock/SKU-1
done | sort | uniq -c
# Debe: 100 x 200, 1 x 429

# 5. Prometheus metrics
METRICS_TOKEN=$(kubectl -n monitoring get secret prometheus-scrape-token -o jsonpath='{.data.token}' | base64 -d)
curl -k -s -H "Authorization: Bearer $METRICS_TOKEN" \
  https://api.andes-retail.example/actuator/prometheus | head -20
```

**Tiempo estimado**: 2-3 min

---

## Timeline estimado total

| Fase | Tiempo | Acumulado |
|---|---|---|
| CDK Deploy (4 stacks) | 25-35 min | 25-35 min |
| K8s Manifests | 5-10 min | 30-45 min |
| App Rollout | 3-5 min | 33-50 min |
| Smoke Tests | 2-3 min | 35-53 min |
| **Total** | **~35-55 min** | |

---

## Post-Deployment Validation Checklist

| Check | Comando | Esperado | ✅/❌ |
|---|---|---|---|
| Stacks CDK | `aws cloudformation list-stacks --stack-status-filter CREATE_COMPLETE UPDATE_COMPLETE --query "StackSummaries[?starts_with(StackName, 'AndesRetail')].StackName"` | 4 stacks | ⏳ |
| EKS Cluster | `aws eks describe-cluster --name andes-retail-staging --query 'cluster.status'` | `ACTIVE` | ⏳ |
| Nodes Ready | `kubectl get nodes` | All `Ready` | ⏳ |
| Deployment | `kubectl -n andes-retail get deploy stock-price-adapter` | `2/2 READY` | ⏳ |
| Pods App | `kubectl -n andes-retail get pods -l app.kubernetes.io/name=stock-price-adapter` | `2 Running, Ready` | ⏳ |
| Kong | `kubectl -n kong get pods` | All `Running` | ⏳ |
| Keycloak | `kubectl -n keycloak get pods` | `2 Running` | ⏳ |
| ESO Secrets | `kubectl -n andes-retail get externalsecret` | `SecretSynced: True` | ⏳ |
| NetworkPolicy | `kubectl -n andes-retail get netpol` | `stock-price-adapter` exists | ⏳ |
| ServiceMonitor | `kubectl -n andes-retail get servicemonitor` | `stock-price-adapter` exists | ⏳ |
| Kong Rate Limit | `kubectl -n kong get kongplugin` | `stock-rate-limit` exists | ⏳ |
| Health Liveness | `curl -k https://api.../actuator/health/liveness` | `200` | ⏳ |
| Health Readiness | `curl -k https://api.../actuator/health/readiness` | `200` | ⏳ |
| Endpoint Stock | `curl -k -H "Auth: Bearer $TOKEN" https://api.../v1/inventory/stock/SMOKE` | `200`, body C-01, header `X-Stock-Source` | ⏳ |
| Rate Limit | 101 req/s → 429 | `100x200, 1x429` | ⏳ |
| Prometheus Scrape | `curl -k -H "Auth: Bearer $METRICS_TOKEN" https://api.../actuator/prometheus` | `200`, métricas CB | ⏳ |
| Circuit Breaker State | `grep resilience4j_circuitbreaker_state /prometheus` | `0` (CLOSED) | ⏳ |

---

## Rollback Plan (si falla)

| Escenario | Acción | Tiempo |
|---|---|---|
| App rollout falla | `kubectl -n andes-retail rollout undo deployment/stock-price-adapter` | < 3 min |
| ConfigMap incorrecto | `kubectl apply -f deploy/k8s/configmap.yaml && kubectl rollout restart deployment/stock-price-adapter` | < 2 min |
| CDK stack falla | `cdk destroy <stack> --force` (orden inverso) + re-deploy | 15-30 min |
| Health checks fallan | Rollback app + investigar logs | < 5 min |

---

## Comunicación

| Evento | Canal | Plantilla |
|---|---|---|
| Deploy iniciado | `#andes-deploys` | `🚀 Deploy v0.1.0 iniciado en staging por @usuario` |
| CDK completo | `#andes-deploys` | `✅ CDK stacks deployed (Network, Security, Data, Compute)` |
| Rollout completo | `#andes-deploys` | `✅ App rollout v0.1.0 completado (2/2 pods)` |
| Smoke tests OK | `#andes-deploys` | `✅ Smoke tests passed - liveness, readiness, stock endpoint, rate limit, metrics` |
| Deploy completado | `#andes-deploys` | `🎉 Deploy v0.1.0 a staging COMPLETADO en XX min` |
| Rollback | `#andes-alerts` | `🔴 ROLLBACK: v0.1.0 reverted - razón: <reason>` |