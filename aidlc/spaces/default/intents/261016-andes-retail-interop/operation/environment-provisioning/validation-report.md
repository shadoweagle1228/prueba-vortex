# Validation Report — `skeleton-stock-query`

## Resumen de validación

| Entorno | Validado | Fecha | Método |
|---|---|---|---|
| **staging** | **Pendiente** (requiere `cdk deploy` + `kubectl apply`) | — | — |
| **prod** | **Pendiente** (requiere `cdk deploy` + `kubectl apply`) | — | — |

> **Nota**: Este reporte documenta los criterios de validación. La validación real se ejecuta tras `cdk deploy` y `kubectl apply` en cada entorno.

---

## Criterios de validación por capa

### 1. Network Stack (`AndesRetailNetworkStack`)

| Check | Comando validación | Esperado | NFR/CA |
|---|---|---|---|
| VPC CIDR | `aws ec2 describe-vpcs --vpc-ids <vpc-id> --query 'Vpcs[0].CidrBlock'` | `10.0.0.0/16` | NFR-04 |
| AZs count | `aws ec2 describe-subnets --filters "Name=vpc-id,Values=<vpc-id>" --query 'length(Subnets)'` | 9 (3 públicos + 3 app + 3 data) | NFR-04 |
| Subnet types | `aws ec2 describe-subnets --filters "Name=vpc-id,Values=<vpc-id>" --query 'Subnets[].Tags[?Key==\`Name\`].Value'` | `public-*`, `private-app-*`, `private-data-*` | CA-01 |
| NAT Gateways | `aws ec2 describe-nat-gateways --filter "Name=vpc-id,Values=<vpc-id>" --query 'length(NatGateways)'` | 3 | NFR-04 |
| Route tables | `aws ec2 describe-route-tables --filters "Name=vpc-id,Values=<vpc-id>"` | 3 RTs (public, app, data) | CA-01 |
| VPC Endpoints | `aws ec2 describe-vpc-endpoints --filters "Name=vpc-id,Values=<vpc-id>"` | S3, DynamoDB, SecretsManager, KMS, ECR | NFR-06, CA-01 |
| No IGW en data RT | `aws ec2 describe-route-tables --route-table-ids <rt-data> --query 'Routes[?GatewayId!=null]'` | Vacío | CA-01 (CDE aislado) |

### 2. Security Stack (`AndesRetailSecurityStack`)

| Check | Comando validación | Esperado | NFR/CA |
|---|---|---|---|
| CMK existe | `aws kms describe-key --key-id alias/andes-retail` | `KeyRotationEnabled: true` | NFR-06 |
| CMK policy | `aws kms get-key-policy --key-id <key-id> --policy-name default` | Permisos: EKS, ElastiCache, Aurora, SecretsManager, CloudWatch | NFR-06 |
| Secret SAP | `aws secretsmanager describe-secret --secret-id andes-retail/sap-credentials` | Existe, `RotationEnabled: false` (manual) | CA-01 |
| Secret Keycloak | `aws secretsmanager describe-secret --secret-id andes-retail/keycloak-admin` | Existe | CA-01 |

### 3. Data Stack (`AndesRetailDataStack`)

| Check | Comando validación | Esperado | NFR/CA |
|---|---|---|---|
| ElastiCache cluster | `aws elasticache describe-replication-groups --replication-group-id andes-retail-redis` | `Status: available`, `MultiAZEnabled: true`, `TransitEncryptionEnabled: true`, `AtRestEncryptionEnabled: true`, `NodeType: cache.t4g.medium` | NFR-01, NFR-04, NFR-06 |
| Redis subnet group | `aws elasticache describe-replication-groups --query 'ReplicationGroups[0].NodeGroups[0].NodeGroupMembers[].CurrentRole'` | Primary + Replica en AZs distintas | NFR-04 |
| Aurora cluster | `aws rds describe-db-clusters --db-cluster-identifier andes-retail-aurora` | `Status: available`, `Engine: aurora-postgresql`, `EngineVersion: 16.1`, `ServerlessV2ScalingConfiguration: true` | NFR-04, FR-11 |
| Aurora encryption | `aws rds describe-db-clusters --query 'DBClusters[0].StorageEncrypted'` | `true` | NFR-06 |
| Aurora backup | `aws rds describe-db-clusters --query 'DBClusters[0].BackupRetentionPeriod'` | `30` | NFR-04 |
| Snapshots auto | `aws rds describe-db-cluster-snapshots --db-cluster-identifier andes-retail-aurora` | Snapshots diarios existen | NFR-04 |

### 4. Compute Stack (`AndesRetailComputeStack`)

| Check | Comando validación | Esperado | NFR/CA |
|---|---|---|---|
| EKS cluster | `aws eks describe-cluster --name andes-retail-staging` | `status: ACTIVE`, `version: 1.29`, `endpoint public+private` | NFR-04 |
| Node Group | `aws eks describe-nodegroup --cluster-name andes-retail-staging --nodegroup-name <ng-name>` | `instanceTypes: ["c7g.medium"]`, `scalingConfig: {min:2,max:6,desired:2}` | NFR-04, CA-02 |
| Addons | `aws eks list-addons --cluster-name andes-retail-staging` | `vpc-cni`, `coredns`, `kube-proxy`, `metrics-server` | NFR-04 |
| kubectl_layer | `cdk synth | grep KubectlV29Layer` | Presente | — |
| Kong Helm | `kubectl -n kong get release kong` | `STATUS: deployed` | NFR-11 |
| Keycloak Helm | `kubectl -n keycloak get release keycloak` | `STATUS: deployed`, réplicas 2 | FR-11 |
| ESO Helm | `kubectl -n external-secrets get release external-secrets` | `STATUS: deployed` | CA-01 |

### 5. Kubernetes Manifests

| Check | Comando validación | Esperado |
|---|---|---|
| Namespace | `kubectl get ns andes-retail` | `Active` |
| Deployment | `kubectl -n andes-retail get deploy stock-price-adapter` | `READY: 2/2`, `AVAILABLE: 2` |
| Pods | `kubectl -n andes-retail get pods -l app.kubernetes.io/name=stock-price-adapter` | `2 Running`, `Ready: True` |
| Startup probe | `kubectl -n andes-retail describe pod <pod> | grep -A5 "Startup Probe"` | `HTTP GET /actuator/health/readiness`, `successThreshold: 1`, `failureThreshold: 24` |
| Readiness probe | `kubectl -n andes-retail describe pod <pod> | grep -A5 "Readiness Probe"` | `HTTP GET /actuator/health/readiness`, `periodSeconds: 10` |
| Liveness probe | `kubectl -n andes-retail describe pod <pod> | grep -A5 "Liveness Probe"` | `HTTP GET /actuator/health/liveness`, `periodSeconds: 30` |
| HPA | `kubectl -n andes-retail get hpa stock-price-adapter` | `TARGETS: <70%`, `MIN: 2`, `MAX: 10` |
| NetworkPolicy | `kubectl -n andes-retail get netpol stock-price-adapter -o yaml` | Ingress: `kong`, `monitoring`; Egress: DNS, Kong, Redis, SAP |
| PDB | `kubectl -n andes-retail get pdb stock-price-adapter` | `MIN AVAILABLE: 1` |
| ServiceMonitor | `kubectl -n andes-retail get servicemonitor stock-price-adapter -o yaml` | `bearerTokenSecret: stock-price-adapter-metrics-token`, path `/actuator/prometheus` |
| Kong Plugin | `kubectl -n kong get kongplugin stock-rate-limit -o yaml` | `limit: 100`, `window: 1` |
| Kong Consumers | `kubectl -n kong get kongconsumer` | 3 consumers: `canal-ecommerce`, `canal-tienda`, `operador-cli` |
| Kong Secrets | `kubectl -n kong get secret` | 3 secrets JWT (HS256/RS256) |
| Kong Ingress | `kubectl -n kong get ingress stock-ingress` | `host: api.andes-retail.example`, path `/v1/inventory` |

### 6. Health Checks Funcionales (post-deploy)

| Check | Comando | Esperado |
|---|---|---|
| Liveness público | `curl -k -s -o /dev/null -w "%{http_code}" https://api.andes-retail.example/actuator/health/liveness` | `200` |
| Readiness público | `curl -k -s -o /dev/null -w "%{http_code}" https://api.andes-retail.example/actuator/health/readiness` | `200` |
| Token Keycloak | `curl -s -X POST https://auth.andes-retail.example/realms/andes-retail/protocol/openid-connect/token -d "grant_type=client_credentials&client_id=canal-ecommerce&client_secret=..." | jq -r .access_token` | Token JWT válido |
| Endpoint stock | `curl -k -s -H "Authorization: Bearer $TOKEN" https://api.andes-retail.example/v1/inventory/stock/SMOKE-TEST` | `200`, body C-01, header `X-Stock-Source` |
| Rate limiting | `for i in {1..101}; do curl -k -s -o /dev/null -w "%{http_code}\n" -H "Authorization: Bearer $TOKEN" https://api.andes-retail.example/v1/inventory/stock/SKU-1; done | sort | uniq -c` | `100 x 200`, `1 x 429` |
| Prometheus scrape | `curl -k -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $METRICS_TOKEN" https://api.andes-retail.example/actuator/prometheus` | `200` |
| Métricas CB | `curl -k -s -H "Authorization: Bearer $METRICS_TOKEN" https://api.../actuator/prometheus | grep resilience4j_circuitbreaker_state` | `resilience4j_circuitbreaker_state{name="sapStockCircuitBreaker"} 0` (CLOSED) |

### 7. Seguridad y Compliance

| Check | Comando validación | Esperado | NFR/CA |
|---|---|---|---|
| TLS 1.3 | `nmap --script ssl-enum-ciphers -p 443 api.andes-retail.example` | Solo TLS 1.3 ciphers | NFR-06 |
| Cert ACM | `aws acm describe-certificate --certificate-arn <arn>` | `Status: ISSUED`, `DomainValidationOptions: SUCCESS` | NFR-06 |
| KMS rotation | `aws kms get-key-rotation-status --key-id <key-id>` | `KeyRotationEnabled: true` | NFR-06 |
| Logs sin PII | `kubectl logs -n andes-retail -l app=stock-price-adapter --tail=100 | jq -r .message | grep -iE 'password|token|secret|credit|pan'` | Sin matches | NFR-08 |
| Correlation-id | `curl -k -s -H "Authorization: Bearer $TOKEN" -H "X-Correlation-ID: test-123" https://api.../v1/inventory/stock/SKU-1 -D - | grep -i x-correlation-id` | `test-123` en response | NFR-08 |
| CDE isolation | `aws ec2 describe-route-tables --route-table-ids <rt-data> --query 'Routes[?GatewayId!=null]'` | Sin IGW | CA-01 |

---

## Resultado global

| Capa | Validado | Issues abiertos |
|---|---|---|
| Network | ❌ Pendiente deploy | — |
| Security | ❌ Pendiente deploy | — |
| Data | ❌ Pendiente deploy | — |
| Compute | ❌ Pendiente deploy | — |
| K8s Manifests | ❌ Pendiente apply | — |
| Health Checks | ❌ Pendiente deploy | — |
| Seguridad | ❌ Pendiente deploy | — |

> **Próximo paso**: Ejecutar `cdk deploy --all --profile andes-staging` y `kubectl apply -k deploy/k8s/ && kubectl apply -k deploy/kong/ && kubectl apply -k deploy/k8s/monitoring/` en entorno staging, luego ejecutar esta validación completa.