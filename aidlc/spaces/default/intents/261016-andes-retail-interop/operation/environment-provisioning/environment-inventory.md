# Environment Inventory — `skeleton-stock-query`

## Referencias upstream

| Artefacto | Ubicación | Qué aporta |
|---|---|---|
| `infrastructure-specification` | `construction/skeleton-stock-query/infrastructure-design/infrastructure-specification.md` | Specs EKS, Redis, Aurora, Kong, Keycloak, NFR-04/06/11/12, sizing |
| `cd-config` | `operation/deployment-pipeline/cd-config.md` | Pipeline CD, cuentas AWS, stacks CDK, promotion gates |

## Entornos AWS

| Entorno | Cuenta ID | Alias | Región | Perfil AWS | Estado |
|---|---|---|---|---|---|
| **staging** | `111122223333` | `andes-staging` | `us-east-1` | `andes-staging` | **Por provisionar** |
| **prod** | `444455556666` | `andes-prod` | `us-east-1` | `andes-prod` | **Por provisionar** |

> **Nota**: Los IDs de cuenta son placeholders. Sustituir por valores reales antes de `cdk deploy`.

---

## Stacks CDK a desplegar (orden de dependencias)

### 1. AndesRetailNetworkStack (`network_stack.py`)
| Recurso | Especificación | NFR/CA |
|---|---|---|
| **VPC** | `10.0.0.0/16`, 3 AZs | NFR-04 (HA) |
| **Subnets públicas** | 3 (1 por AZ), `/24` | NAT Gateway, ALB/Kong |
| **Subnets privadas (app)** | 3 (1 por AZ), `/24` | EKS nodes, pods |
| **Subnets privadas (data/CDE)** | 3 (1 por AZ), `/24` | **CA-01** (CDE aislado), Redis, Aurora |
| **Internet Gateway** | 1 | Tráfico público |
| **NAT Gateways** | 3 (1 por AZ pública) | Egress privado |
| **Route Tables** | Pública, Privada-app, Privada-data | Aislamiento CDE |
| **Security Groups** | VPC endpoint SG, EKS SG, Redis SG, Aurora SG | NFR-06 (TLS), CA-01 |

### 2. AndesRetailSecurityStack (`security_stack.py`)
| Recurso | Especificación | NFR/CA |
|---|---|---|
| **KMS Key (CMK)** | `SYMMETRIC_DEFAULT`, rotación anual, alias `andes-retail` | NFR-06, CA-01 |
| **KMS Key (Redis/Aurora)** | Mismo CMK o dedicado | NFR-06 |
| **Secrets Manager** | Secret `andes-retail/sap-credentials` (SAP user/pass) | CA-01 |
| **Secrets Manager** | Secret `andes-retail/keycloak-admin` (admin/pass) | CA-01 |

### 3. AndesRetailDataStack (`data_stack.py`)
| Recurso | Especificación | NFR/CA |
|---|---|---|
| **ElastiCache Redis** | `cache.t4g.medium`, Multi-AZ (2 nodos), Redis 7.1, TLS in-transit, encryption at-rest (KMS), `cache.t4g.medium` | NFR-01 (latencia), NFR-04 (HA), NFR-06 (KMS), BR-STK-01/03 |
| **Aurora PostgreSQL** | Serverless v2, PostgreSQL 16.1, Multi-AZ, encryption at-rest (KMS), backup 30 días, point-in-time recovery | NFR-04, NFR-06, FR-11 (Keycloak DB) |
| **Subnet Group (Redis)** | Subnets CDE (privadas data) | CA-01 |
| **Subnet Group (Aurora)** | Subnets CDE (privadas data) | CA-01 |
| **Parameter Groups** | Redis 7.1, PostgreSQL 16 | — |

### 4. AndesRetailComputeStack (`compute_stack.py`)
| Recurso | Especificación | NFR/CA |
|---|---|---|
| **EKS Cluster** | 1.29, endpoint público + privado, `kubectl_layer` (KubectlV29Layer) | NFR-04, NFR-11 |
| **Node Group** | `c7g.medium` (Graviton3), 2-6 nodos, `system` + `critical-addons` taints | NFR-04, NFR-12, CA-02 |
| **Addons** | `vpc-cni`, `coredns`, `kube-proxy`, `metrics-server` | NFR-04, observabilidad |
| **Kong Ingress Controller** | Helm `kong/kong` v2.33, `ingressClass: kong`, rate limiting plugin | NFR-11 |
| **Keycloak** | Helm `bitnami/keycloak` v24.0, PostgreSQL externo (Aurora), HA 2 réplicas | FR-11, NFR-11 |
| **External Secrets Operator** | Helm `external-secrets/external-secrets` v0.9, SecretStore AWS Secrets Manager | CA-01, secrets sync |
| **IAM Roles** | `eks-node-role`, `kong-sa`, `keycloak-sa`, `eso-sa` (IRSA) | Least privilege |

---

## Recursos Kubernetes (desplegados tras CDK)

| Recurso | Namespace | Archivo(s) | Descripción |
|---|---|---|---|
| `Namespace` | `andes-retail` | `deploy/k8s/namespace.yaml` | App namespace |
| `ServiceAccount` | `andes-retail` | `deploy/k8s/serviceaccount.yaml` | IRSA para app |
| `ConfigMap` | `andes-retail` | `deploy/k8s/configmap.yaml` | Config app (Redis, OIDC, cache TTL) |
| `Deployment` | `andes-retail` | `deploy/k8s/deployment.yaml` | 2 réplicas, rolling, probes, securityContext |
| `Service` | `andes-retail` | `deploy/k8s/service.yaml` | ClusterIP 8080, selector app |
| `HPA` | `andes-retail` | `deploy/k8s/hpa.yaml` | CPU 70%, 2-10 replicas |
| `NetworkPolicy` | `andes-retail` | `deploy/k8s/networkpolicy.yaml` | Ingress: kong, monitoring; Egress: DNS, Kong, Redis, SAP |
| `PodDisruptionBudget` | `andes-retail` | `deploy/k8s/networkpolicy.yaml` | minAvailable: 1 |
| `ServiceMonitor` | `andes-retail` | `deploy/k8s/monitoring/servicemonitor.yaml` | Prometheus scrape `/actuator/prometheus` bearer |
| `KongPlugin (rate-limit)` | `kong` | `deploy/kong/kong-plugin-ratelimit.yaml` | 100 req/s por consumer |
| `KongConsumers + Secrets` | `kong` | `deploy/kong/kong-consumers.yaml` | 3 consumers: ecommerce, tienda, operador |
| `Ingress (Kong)` | `kong` | `deploy/kong/kong-plugin-ratelimit.yaml` | `/v1/inventory` → service |

---

## Secretos y parámetros (gestionados por External Secrets Operator)

| Secret / Param | Origen | Destino | Descripción |
|---|---|---|---|
| `sap-credentials` | Secrets Manager `andes-retail/sap-credentials` | K8s Secret `stock-price-adapter-secrets` | SAP user/pass/base-url |
| `keycloak-admin` | Secrets Manager `andes-retail/keycloak-admin` | Keycloak Helm values | Admin user/pass |
| `stock-price-adapter-metrics-token` | Secrets Manager (generado) | K8s Secret `stock-price-adapter-metrics-token` | Prometheus scrape bearer |
| `kong-jwt-keys` | Keycloak JWKS / generado | Kong Secret `stock-jwt-secret` | RS256 keys para JWT validation |

---

## Conectividad validada (post-provisioning)

| Camino | Origen | Destino | Puerto | Protocolo | Validación |
|---|---|---|---|---|---|
| **Ingress público** | Internet | Kong (ALB) | 443 | HTTPS | ACM cert, ALB health check |
| **Kong → App** | Kong (pod) | stock-price-adapter (svc) | 8080 | HTTP | NetworkPolicy ingress `kong` |
| **App → Redis** | App (pod) | ElastiCache | 6379 | TLS | SG Redis permite app SG, TLS verify |
| **App → Aurora (Keycloak)** | Keycloak (pod) | Aurora | 5432 | TLS | SG Aurora permite Keycloak SG |
| **App → SAP** | App (pod) | On-prem / VPN | 443/9000 | HTTPS | NetworkPolicy egress 0.0.0.0/0 (except metadata) |
| **Prometheus → App** | Prometheus (monitoring ns) | App (svc) | 8080 | HTTP + Bearer | NetworkPolicy ingress `monitoring`, ServiceMonitor |
| **Keycloak → App (JWKS)** | App (pod) | Keycloak (svc) | 8080/443 | HTTP/HTTPS | NetworkPolicy egress `kong` (asume Kong proxy) |

---

## Checklist de validación post-provisioning

### Network Stack
- [ ] VPC `10.0.0.0/16` creada con 3 AZs
- [ ] 3 subnets públicas, 3 privadas app, 3 privadas data (CDE)
- [ ] NAT Gateways en AZs públicas (3)
- [ ] Route tables: pública → IGW; privadas app → NAT; privadas data → sin ruta a IGW
- [ ] Security Groups: EKS, Redis, Aurora, Kong, Keycloak creados
- [ ] VPC Endpoints: S3, DynamoDB, SecretsManager, KMS, ECR (Gateway/Interface)

### Security Stack
- [ ] CMK `andes-retail` creado, rotación anual habilitada
- [ ] Secrets Manager: `sap-credentials`, `keycloak-admin` creados
- [ ] Políticas KMS: permisos para EKS, ElastiCache, Aurora, SecretsManager

### Data Stack
- [ ] ElastiCache: cluster `andes-retail-redis`, Multi-AZ, `cache.t4g.medium`, TLS, encryption at-rest (CMK)
- [ ] Aurora: cluster `andes-retail-aurora`, Serverless v2, PostgreSQL 16.1, Multi-AZ, backup 30d, encryption (CMK)
- [ ] Subnet groups usan subnets CDE
- [ ] Snapshots automáticos: Redis diario, Aurora 30 días + PITR

### Compute Stack
- [ ] EKS `andes-retail-staging` v1.29, endpoint público+privado, `kubectl_layer` ok
- [ ] Node Group: `c7g.medium`, 2-6, `system`/`critical-addons` taints, `metrics-server` addon
- [ ] Kong: Helm deployado, `ingressClass: kong`, rate limiting plugin configurado
- [ ] Keycloak: Helm deployado, BD externa Aurora, 2 réplicas, realm `andes-retail` importado
- [ ] ESO: Helm deployado, `ClusterSecretStore` AWS Secrets Manager, `ExternalSecret` recursos creados

### Kubernetes manifests
- [ ] `kubectl apply -k deploy/k8s/` exitoso
- [ ] `kubectl apply -k deploy/kong/` exitoso
- [ ] `kubectl apply -k deploy/k8s/monitoring/` exitoso
- [ ] Deployment `stock-price-adapter`: 2/2 Ready, probes OK
- [ ] HPA: métricas CPU funcionando
- [ ] NetworkPolicy: ingress kong+monitoring, egress DNS+Kong+Redis+SAP
- [ ] ServiceMonitor: scrape `/actuator/prometheus` con bearer token
- [ ] Kong: rate limit plugin 100 req/s, 3 consumers con secrets, Ingress `/v1/inventory`

### Health checks finales
- [ ] `curl -k https://api.andes-retail.example/actuator/health/liveness` → 200
- [ ] `curl -k https://api.andes-retail.example/actuator/health/readiness` → 200
- [ ] Token Keycloak → `GET /v1/inventory/stock/SMOKE` → 200 + `X-Stock-Source`
- [ ] Prometheus scrapea `/actuator/prometheus` → métricas visibles
- [ ] Rate limiting: 101 req/s mismo consumer → 429
- [ ] Logs JSON estructurado + correlation-id propagado