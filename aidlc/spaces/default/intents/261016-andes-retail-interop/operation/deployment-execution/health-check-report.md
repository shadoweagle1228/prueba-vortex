# Health Check Report — `skeleton-stock-query`

## Resumen de salud post-deploy

| Entorno | Verificado | Fecha | Estado global |
|---|---|---|---|
| **staging** | **Pendiente** (requiere deploy real) | — | — |
| **prod** | **Pendiente** | — | — |

> Este reporte define los health checks a validar tras cada deploy. Los resultados reales se registrarán tras cada ejecución.

---

## 1. Infraestructura (AWS)

### EKS Cluster
| Componente | Check | Comando | Esperado | Estado |
|---|---|---|---|---|
| Cluster status | `aws eks describe-cluster` | `status: ACTIVE` | ⏳ |
| Endpoint público | `aws eks describe-cluster` | `endpoint` accesible | ⏳ |
| Endpoint privado | `aws eks describe-cluster` | `privateEndpoint` habilitado | ⏳ |
| Version | `aws eks describe-cluster` | `1.29` | ⏳ |
| Node Group | `aws eks describe-nodegroup` | `ACTIVE`, desired=2, min=2, max=6 | ⏳ |
| Nodes Ready | `kubectl get nodes` | All `Ready`, `c7g.medium` | ⏳ |
| Addons | `aws eks list-addons` | `vpc-cni`, `coredns`, `kube-proxy`, `metrics-server` | ⏳ |

### ElastiCache Redis
| Componente | Check | Comando | Esperado | Estado |
|---|---|---|---|---|
| Cluster status | `aws elasticache describe-replication-groups` | `available` | ⏳ |
| Multi-AZ | `aws elasticache describe-replication-groups` | `MultiAZEnabled: true` | ⏳ |
| Nodes | Primary + Replica en AZs distintas | ⏳ |
| TLS in-transit | `TransitEncryptionEnabled: true` | ⏳ |
| Encryption at-rest | `AtRestEncryptionEnabled: true` (KMS) | ⏳ |
| CPU/Memory | CloudWatch `CPUUtilization`, `FreeableMemory` | < 70% | ⏳ |

### Aurora PostgreSQL
| Componente | Check | Comando | Esperado | Estado |
|---|---|---|---|---|
| Cluster status | `aws rds describe-db-clusters` | `available` | ⏳ |
| Engine | PostgreSQL 16.1 | ⏳ |
| Serverless v2 | `ServerlessV2ScalingConfiguration` activo | ⏳ |
| Multi-AZ | 2 AZs, writer + reader | ⏳ |
| Encryption | `StorageEncrypted: true` (KMS) | ⏳ |
| Backup | `BackupRetentionPeriod: 30`, PITR habilitado | ⏳ |

### Networking
| Componente | Check | Comando | Esperado | Estado |
|---|---|---|---|---|
| VPC | `aws ec2 describe-vpcs` | `10.0.0.0/16`, 3 AZs | ⏳ |
| Subnets | 9 subnets (3 pub, 3 app, 3 data) | ⏳ |
| NAT Gateways | 3 (1 por AZ pública) | ⏳ |
| Route Tables | 3 (public, app, data) | ⏳ |
| Data RT sin IGW | Verificar | ⏳ |
| VPC Endpoints | S3, DynamoDB, SecretsManager, KMS, ECR | ⏳ |
| Security Groups | EKS, Redis, Aurora, Kong, Keycloak | ⏳ |

---

## 2. Kubernetes (EKS)

### Cluster Health
| Componente | Check | Comando | Esperado | Estado |
|---|---|---|---|---|
| API Server | `kubectl get --raw='/readyz?verbose'` | `ok` | ⏳ |
| CoreDNS | `kubectl -n kube-system get pods -l k8s-app=kube-dns` | 2 Running | ⏳ |
| Metrics Server | `kubectl -n kube-system get deploy metrics-server` | 1/1 Ready | ⏳ |
| VPC CNI | `kubectl -n kube-system get pods -l k8s-app=aws-node` | All Ready | ⏳ |
| kube-proxy | `kubectl -n kube-system get pods -l k8s-app=kube-proxy` | All Ready | ⏳ |

### Namespace `andes-retail`
| Recurso | Check | Comando | Esperado | Estado |
|---|---|---|---|---|
| Deployment | `kubectl get deploy stock-price-adapter` | `2/2 READY`, `AVAILABLE 2` | ⏳ |
| Pods | `kubectl get pods -l app.kubernetes.io/name=stock-price-adapter` | 2 Running, Ready | ⏳ |
| Startup Probe | `kubectl describe pod <pod>` | `HTTP GET /actuator/health/readiness`, success=1, failure=24 | ⏳ |
| Readiness Probe | `kubectl describe pod <pod>` | `HTTP GET /actuator/health/readiness`, period=10s | ⏳ |
| Liveness Probe | `kubectl describe pod <pod>` | `HTTP GET /actuator/health/liveness`, period=30s | ⏳ |
| HPA | `kubectl get hpa stock-price-adapter` | `TARGETS <70%`, min=2, max=10 | ⏳ |
| NetworkPolicy | `kubectl get netpol` | `stock-price-adapter` (ingress kong+monitoring, egress DNS+Kong+Redis+SAP) | ⏳ |
| PDB | `kubectl get pdb` | `stock-price-adapter` minAvailable=1 | ⏳ |
| ServiceMonitor | `kubectl get servicemonitor` | `stock-price-adapter` (bearerTokenSecret, path /actuator/prometheus) | ⏳ |

### Namespace `kong`
| Recurso | Check | Comando | Esperado | Estado |
|---|---|---|---|---|
| Kong Pods | `kubectl -n kong get pods` | All Running, Ready | ⏳ |
| Kong Service | `kubectl -n kong get svc` | ClusterIP + LoadBalancer (ALB) | ⏳ |
| Rate Limit Plugin | `kubectl -n kong get kongplugin` | `stock-rate-limit` (limit=100, window=1) | ⏳ |
| Consumers | `kubectl -n kong get kongconsumer` | 3 (canal-ecommerce, canal-tienda, operador-cli) | ⏳ |
| Consumer Secrets | `kubectl -n kong get secret` | 3 JWT secrets | ⏳ |
| Ingress | `kubectl -n kong get ingress` | `stock-ingress` (host api.andes-retail.example, path /v1/inventory) | ⏳ |

### Namespace `keycloak`
| Recurso | Check | Comando | Esperado | Estado |
|---|---|---|---|---|
| Keycloak Pods | `kubectl -n keycloak get pods` | 2 Running, Ready | ⏳ |
| Keycloak Service | `kubectl -n keycloak get svc` | ClusterIP | ⏳ |
| Realm importado | `kubectl -n keycloak exec <pod> -- /opt/keycloak/bin/kcadm.sh get realms/andes-retail` | Realm existe | ⏳ |

### Namespace `external-secrets`
| Recurso | Check | Comando | Esperado | Estado |
|---|---|---|---|---|
| ESO Pods | `kubectl -n external-secrets get pods` | All Running | ⏳ |
| ClusterSecretStore | `kubectl get clustersecretstore` | `aws-secrets-manager` (Ready) | ⏳ |
| ExternalSecrets | `kubectl -n andes-retail get externalsecret` | All `SecretSynced: True` | ⏳ |

### Namespace `monitoring`
| Recurso | Check | Comando | Esperado | Estado |
|---|---|---|---|---|
| Prometheus | `kubectl -n monitoring get pods` | Running | ⏳ |
| ServiceMonitor | `kubectl -n monitoring get servicemonitor` | `stock-price-adapter` | ⏳ |

---

## 3. Aplicación (stock-price-adapter)

### Health Endpoints
| Endpoint | Comando | Esperado | Estado |
|---|---|---|---|
| `/actuator/health/liveness` | `curl -k https://api.../actuator/health/liveness` | `200`, `{"status":"UP"}` | ⏳ |
| `/actuator/health/readiness` | `curl -k https://api.../actuator/health/readiness` | `200`, `{"status":"UP","components":{"readinessState":"UP"}}` | ⏳ |
| `/actuator/health` (detalle) | `curl -k -H "Auth: Bearer $TOKEN" https://api.../actuator/health` | `200`, componentes: `db`, `redis`, `circuitBreakers`, `diskSpace` | ⏳ |

### Métricas (Prometheus)
| Métrica | Comando | Esperado | Estado |
|---|---|---|---|
| Scrape exitoso | `curl -k -H "Auth: Bearer $METRICS_TOKEN" https://api.../actuator/prometheus` | `200`, texto Prometheus | ⏳ |
| `resilience4j_circuitbreaker_state{sapStockCircuitBreaker}` | `grep ...` | `0` (CLOSED) | ⏳ |
| `resilience4j_bulkhead_available_concurrent_calls{sapStockBulkhead}` | `grep ...` | `≥ 15` | ⏳ |
| `http_server_requests_seconds_count{uri="/v1/inventory/stock/**"}` | `grep ...` | Contador > 0 | ⏳ |
| `jvm_memory_used_bytes` | `grep ...` | Presente | ⏳ |
| `jvm_threads_live` | `grep ...` | Presente | ⏳ |

### Logs
| Check | Comando | Esperado | Estado |
|---|---|---|---|
| Formato JSON | `kubectl logs -n andes-retail -l app=stock-price-adapter --tail=10 | jq .` | JSON válido | ⏳ |
| Correlation-ID | `kubectl logs ... | jq -r .correlationId` | UUID presente | ⏳ |
| Sin PII | `kubectl logs ... | jq -r .message | grep -i password/token/secret` | Sin matches | ⏳ |
| Nivel | `kubectl logs ... | jq -r .level` | `INFO` o superior | ⏳ |

---

## 4. Seguridad

| Check | Comando | Esperado | Estado |
|---|---|---|---|
| TLS 1.3 | `nmap --script ssl-enum-ciphers -p 443 api.andes-retail.example` | Solo TLS 1.3 | ⏳ |
| Cert ACM | `aws acm describe-certificate --certificate-arn <arn>` | `ISSUED`, validación SUCCESS | ⏳ |
| KMS Rotation | `aws kms get-key-rotation-status --key-id <key-id>` | `KeyRotationEnabled: true` | ⏳ |
| Secrets en SecretsManager | `aws secretsmanager list-secrets` | `andes-retail/sap-credentials`, `andes-retail/keycloak-admin` | ⏳ |
| ExternalSecrets sincronizados | `kubectl -n andes-retail get externalsecret` | All `SecretSynced: True` | ⏳ |
| Rate Limit Kong | 101 req/s → 429 | Verificado en smoke tests | ⏳ |
| JWKS Resolution | `curl https://auth.../protocol/openid-connect/certs` | `200`, keys válidas | ⏳ |

---

## 5. Resumen Global

| Categoría | Checks totales | Pass | Fail | Pendiente | Estado |
|---|---|---|---|---|---|
| Infraestructura AWS | 18 | 0 | 0 | 18 | ⏳ |
| Kubernetes | 22 | 0 | 0 | 22 | ⏳ |
| Aplicación | 8 | 0 | 0 | 8 | ⏳ |
| Seguridad | 7 | 0 | 0 | 7 | ⏳ |
| **TOTAL** | **55** | **0** | **0** | **55** | **⏳ PENDIENTE DEPLOY** |

---

## Próximos pasos

1. Ejecutar `cdk deploy --all --profile andes-staging`
2. Ejecutar `kubectl apply -k deploy/k8s/ && kubectl apply -k deploy/kong/ && kubectl apply -k deploy/k8s/monitoring/`
3. Ejecutar smoke tests (`smoke-test-results.md`)
4. Completar este health check report
5. Si todo PASS → aprobar para promoción a prod