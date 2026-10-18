# Cost Analysis & Optimization — `skeleton-stock-query`

## Referencias upstream

| Artefacto | Ubicación | Qué aporta |
|---|---|---|
| `infrastructure-specification` | `construction/skeleton-stock-query/infrastructure-design/infrastructure-specification.md` | Specs infra, sizing |
| `dashboards.md` | `operation/observability-setup/dashboards.md` | Métricas de utilización |
| `performance-validation` | `operation/performance-validation/` | Load test results, capacity |

---

## Resumen de Costos (Estimado Mensual USD)

| Categoría | Componente | Staging | Prod (Estimado) | % Total |
|---|---|---|---|---|
| **Compute (EKS)** | 3x c7g.medium (2-6 nodes ASG) | $120 | $240-480 | 25% |
| **Cache (Redis)** | cache.t4g.medium Multi-AZ | $60 | $120-240 | 10% |
| **Database (Aurora)** | Serverless v2 (0.5-128 ACU) | $80-200 | $300-800 | 20% |
| **Network (ALB, NAT, VPC)** | 2 AZs, Data Transfer | $100 | $200-400 | 15% |
| **Observability** | CW Logs, Metrics, X-Ray, Alarms | $50 | $100-200 | 8% |
| **Security (KMS, Secrets, WAF)** | CMK, Secrets Manager, WAF | $30 | $50-100 | 5% |
| **Kong/Keycloak (EKS)** | Incluido en EKS nodes | Incluido | Incluido | 5% |
| **Backup/DR** | Snapshots, PITR, Replication | $20 | $50-100 | 3% |
| **Soporte AWS (Business)** | 10% de gasto AWS | ~$50 | ~$150 | 4% |
| **Contingencia (15%)** | Buffer | ~$75 | ~$225 | 5% |
| **TOTAL ESTIMADO** | | **~$655-805/mes** | **~$1,400-2,500/mes** | 100% |

> **Nota**: Costos basados en pricing AWS us-east-1 2024. Prod varía según tráfico real. Staging ~40% de prod.

---

## Desglose por Componente

### EKS Cluster (`andes-retail-staging/prod`)
| Recurso | Config | Costo Unitario | Cantidad | Costo Mes |
|---|---|---|---|---|
| EKS Control Plane | $0.10/hora | $73 | 1 | $73 |
| Node Group (c7g.medium) | 4 vCPU, 8GB, $0.034/hr | $25 | 3-6 (ASG) | $75-150 |
| EBS Volumes (nodes) | gp3 20GB | $2.40 | 3-6 | $7-15 |
| Load Balancer (ALB) | $0.0225/hr + LCU | ~$25 | 1 | $25 |
| **Subtotal EKS** | | | | **$200-263** |

### ElastiCache Redis (`andes-retail-redis`)
| Recurso | Config | Costo Unitario | Cantidad | Costo Mes |
|---|---|---|---|---|
| Cache Nodes (t4g.medium) | $0.052/hr | $38 | 2 (Multi-AZ) | $76 |
| Snapshot Storage | $0.085/GB-mes | $0.085 | 10 GB | $1 |
| Data Transfer (AZ) | $0.01/GB | Variable | - | ~$5 |
| **Subtotal Redis** | | | | **~$82** |

### Aurora PostgreSQL Serverless v2 (`andes-retail-aurora`)
| Recurso | Config | Costo Unitario | Estimado Mes |
|---|---|---|---|
| ACU (Aurora Capacity Units) | $0.12/ACU-hr | 0.5-128 ACU | $50-200 (variable) |
| Storage | $0.10/GB-mes | 20-100 GB | $2-10 |
| Backup Storage | $0.021/GB-mes | 30 días retention | $5-15 |
| I/O Requests | $0.20/M requests | ~10M/mes | $2 |
| **Subtotal Aurora** | | | **$57-227** |

### Observabilidad
| Servicio | Detalle | Costo Mes (Staging) | Costo Mes (Prod) |
|---|---|---|---|
| CloudWatch Logs | 10 GB ingest, 30d retention | $25 | $50 |
| CloudWatch Metrics | 500 custom metrics, 15d retention | $15 | $30 |
| CloudWatch Alarms | 50 alarmas | $5 | $10 |
| CloudWatch Anomaly Detection | 10 métricas | $5 | $10 |
| X-Ray Traces | 10% sampling, ~100K/day | $10 | $50 |
| Grafana Cloud (opcional) | Si no self-hosted | $0 | $50 |
| **Subtotal Observabilidad** | | **~$60** | **~$200** |

### Seguridad y Compliance
| Servicio | Detalle | Costo Mes |
|---|---|---|
| KMS (CMK) | 1 key, 1M requests | $1 + $0.03 = ~$2 |
| Secrets Manager | 5 secrets, 10K requests | $2 + $0.05 = ~$3 |
| WAF (si aplica) | 1 Web ACL, 10 reglas | ~$15 |
| Config Rules | 20 rules | $40 |
| **Subtotal Seguridad** | | **~$60** |

---

## Oportunidades de Optimización (Cost Savings)

### 1. Compute (EKS) - **Alto Impacto**
| Optimización | Ahorro Estimado | Esfuerzo | Riesgo |
|---|---|---|---|
| **Savings Plans (1yr/3yr)** | 30-50% en EC2 | Bajo (compra) | Bajo |
| **Spot Instances (Workloads tolerantes)** | 60-90% en nodes | Medio (config HPA + tolerations) | Medio (evicción) |
| **Graviton3 (c7g) ya usado** | Ya implementado | - | - |
| **Right-sizing nodes** | c7g.medium → c7g.large si CPU > 70% | Bajo | Bajo |
| **Cluster Autoscaler tuning** | Scale-down delay 10m → 5m | Bajo | Bajo |

### 2. Aurora Serverless v2 - **Medio Impacto**
| Optimización | Ahorro Estimado | Esfuerzo | Riesgo |
|---|---|---|---|
| **Min ACU 0.5** (ya configurado) | Base | - | - |
| **Max ACU tuning** | 128 → 64 si carga < 50% | Bajo | Bajo |
| **Pause/Resume (dev/test)** | 100% fuera horario | Medio (automatización) | Bajo |
| **Provisioned + Serverless hybrid** | Si carga estable > 50% ACU | Alto | Medio |

### 3. ElastiCache Redis - **Medio Impacto**
| Optimización | Ahorro Estimado | Esfuerzo | Riesgo |
|---|---|---|---|
| **Node type right-sizing** | t4g.medium → t4g.small si memoria < 50% | Bajo | Medio |
| **Reservation (1yr/3yr)** | 30-50% | Bajo | Bajo |
| **TTL Optimization** | Reducir TTL stale 3600s → 1800s si memoria presión | Bajo | Bajo |
| **Lazy Loading / Write-Through** | Ya implementado (cache-aside) | - | - |

### 3. Network & Data Transfer - **Bajo Impacto**
| Optimización | Ahorro Estimado | Esfuerzo | Riesgo |
|---|---|---|---|
| **VPC Endpoints (Gateway)** | S3, DynamoDB gratis (ya implementado) | - | - |
| **Interface Endpoints** | Solo necesarios (SecretsManager, KMS, ECR) | Bajo | Bajo |
| **CloudFront para assets estáticos** | Si hay assets frontend | Medio | Bajo |

### 4. Observabilidad - **Bajo Impacto**
| Optimización | Ahorro Estimado | Esfuerzo | Riesgo |
|---|---|---|---|
| **Log Retention** | 30d → 14d para debug logs | Bajo | Bajo (compliance) |
| **Metric Cardinality** | Reducir labels alta cardinalidad | Medio | Bajo |
| **X-Ray Sampling** | 10% → 5% en prod estable | Bajo | Bajo |
| **Log Export S3** | Glacier Deep Archive 1yr | Bajo | Bajo |

### 5. KMS & Secrets - **Mínimo**
| Optimización | Ahorro | Esfuerzo |
|---|---|---|
| CMK Rotation | Ya automática (anual) | - |
| Secrets Rotation | Automatizar (Lambda/EventBridge) | Medio |

---

## Proyección de Costos (Escenarios)

| Escenario | Tráfico (RPS) | Nodos EKS | Aurora ACU | Redis | Costo Mes (USD) |
|---|---|---|---|---|---|
| **Bajo (Staging)** | 50 RPS | 2 | 1 ACU | t4g.medium | ~$350 |
| **Base (Prod Inicial)** | 200 RPS | 3 | 4 ACU | t4g.medium | ~$1,200 |
| **Crecimiento (6 meses)** | 500 RPS | 6 | 16 ACU | t4g.large | ~$2,500 |
| **Pico (Black Friday)** | 2000 RPS | 10 (max) | 64 ACU | r6g.xlarge | ~$5,000 |
| **Multi-Región (DR)** | Activo-Pasivo | x2 | Global DB | Global Datastore | x1.8 |

---

## Recomendaciones de Gobernanza de Costos

| Acción | Frecuencia | Responsable | Herramienta |
|---|---|---|---|
| **Revisar Cost Explorer** | Semanal | FinOps / EM | AWS Cost Explorer |
| **Budget Alerts (80% forecast)** | Configurar una vez | FinOps | AWS Budgets |
| **Rightsizing Review** | Mensual | Platform/DevOps | Compute Optimizer |
| **Unused Resources Audit** | Mensual | Platform | Trusted Advisor / Config |
| **Savings Plans Renewal** | Anual (antes expiración) | FinOps | Savings Plans Console |
| **Tagging Compliance** | Semanal (automatizado) | Platform | Config Rules / Tag Policies |

### Tagging Strategy (Obligatorio)
| Tag | Valor | Obligatorio |
|---|---|---|
| `Project` | `AndesRetail` | Sí |
| `Environment` | `staging` \| `prod` | Sí |
| `Team` | `Platform` \| `Backend` \| `Data` | Sí |
| `CostCenter` | `Engineering` | Sí |
| `Owner` | `email@andes-retail.com` | Sí |
| `AutoDelete` | `true` \| `false` (para recursos temporales) | No |

---

## Reporte de Costos Mensual (Template)

| Mes | Gasto Real | Presupuesto | Variación | Top 3 Drivers | Acciones |
|---|---|---|---|---|---|
| Ene 2026 | $XXX | $XXX | +X% | 1. EKS, 2. Aurora, 3. Data Transfer | Right-sizing nodes |
| Feb 2026 | $XXX | $XXX | -X% | 1. EKS, 2. Aurora, 3. Data Transfer | Savings Plan compra |
| ... | ... | ... | ... | ... | ... |

---

## Próximos Pasos (Cost Optimization Roadmap)

| Trimestre | Acción | Ahorro Estimado | Responsable |
|---|---|---|---|
| **Q1** | Comprar Savings Plans 1yr (EKS, Aurora, Redis) | 30-40% compute | FinOps |
| **Q1** | Implementar Spot para batch/non-critical workloads | 20-30% nodes | Platform |
| **Q2** | Right-sizing Aurora (Max ACU tuning) | 10-20% DB | Platform |
| **Q2** | Evaluar Redis Cluster Mode vs Vertical scaling | 20-30% Cache | Platform |
| **Q3** | Evaluar Graviton4 / c8g cuando disponible | 15-20% compute | Platform |
| **Q4** | Renovar Savings Plans / ajustar coberturas | Optimización continua | FinOps |