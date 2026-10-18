# Environment Provisioning Questions — `skeleton-stock-query`

## Preguntas de clarificación (resueltas)

### 1. ¿Todos los entornos están provisionados según Infra Design?
**Respuesta**: **Pendiente** — Los stacks CDK (`network_stack.py`, `security_stack.py`, `data_stack.py`, `compute_stack.py`) están implementados y sintetizan correctamente (`py -3 infra/app.py` → EXIT 0). El despliegue real a AWS (`cdk deploy`) está pendiente de credenciales y cuenta destino.

### 2. ¿VPCs, subnets, security groups, NACLs son correctos?
**Respuesta**: **Validado en diseño** — `network_stack.py` implementa:
- VPC `10.0.0.0/16` con 3 AZs
- 3 subnets públicas, 3 privadas app, 3 privadas data (CDE)
- NAT Gateways en AZs públicas (3)
- Route tables separadas (pública, app, data — data sin IGW)
- Security Groups para EKS, Redis, Aurora, Kong, Keycloak
- VPC Endpoints (S3, DynamoDB, SecretsManager, KMS, ECR)
- NACLs por defecto (allow all) — si se requiere endurecer, añadir reglas deny

### 3. ¿Secretos en Secrets Manager / Parameter Store correctamente inyectados?
**Respuesta**: **Diseñado, pendiente deploy** — `security_stack.py` crea:
- CMK `andes-retail` (rotación anual)
- Secret `andes-retail/sap-credentials` (user/pass/base-url)
- Secret `andes-retail/keycloak-admin` (admin/pass)
- `compute_stack.py` instala External Secrets Operator (Helm) con `ClusterSecretStore` AWS Secrets Manager
- `ExternalSecret` recursos definidos para sincronizar a K8s secrets

### 4. ¿Conectividad cross-account / cross-VPC validada?
**Respuesta**: **Single account, single VPC** (diseño actual). Si se requiere multi-account (ej. cuenta separada para prod), usar:
- VPC Peering o Transit Gateway
- IAM roles cross-account para CDK deploy
- Route 53 Resolver rules para DNS privado

---

## Preguntas abiertas / decisiones pendientes

### 5. ¿Cuentas AWS definitivas para staging y prod?
| Entorno | Cuenta ID (placeholder) | Decisión requerida |
|---|---|---|
| **staging** | `111122223333` | Confirmar cuenta real, configurar `andes-staging` profile |
| **prod** | `444455556666` | Confirmar cuenta real, configurar `andes-prod` profile |

**Acción**: Sustituir placeholders en `cdk.json` context y `~/.aws/config` antes de primer deploy.

### 6. ¿Certificados TLS para dominios públicos?
| Dominio | Tipo | Acción |
|---|---|---|
| `api.andes-retail.example` | ACM (us-east-1) | Solicitar certificado, validar DNS |
| `auth.andes-retail.example` | ACM (us-east-1) | Mismo cert (SAN) o separado |

**Decisión**: Usar certificado wildcard `*.andes-retail.example` o SAN único. Validar vía DNS (Route 53) o email.

### 7. ¿Dominio base y Route 53 hosted zone?
- **Hosted zone**: `andes-retail.example` (crear en Route 53 o importar)
- **Registros**: 
  - `api` → ALB Kong (alias)
  - `auth` → ALB Keycloak (alias, si Keycloak expone públicamente) o interno
- **Decisión**: ¿Keycloak expuesto públicamente o solo vía Kong? Actual: Keycloak ClusterIP, JWKS via Kong proxy.

### 8. ¿Parámetros de sizing definitivos?
| Recurso | Actual (diseño) | Confirmar / Ajustar |
|---|---|---|
| Redis node type | `cache.t4g.medium` | ✅ Graviton3, coste/performance OK |
| Redis Multi-AZ | 2 nodos (primary + replica) | ✅ NFR-04 |
| Aurora Serverless v2 | Min 0.5 ACU, Max 128 ACU | Revisar según carga esperada |
| EKS node type | `c7g.medium` (Graviton3) | ✅ ARM64, coste/performance |
| Node group size | 2-6 (desired 2) | Ajustar desired según baseline tráfico |
| Kong replicas | 2 (Helm default) | Ajustar si HA requerido |

### 8. ¿Backup y retención definitivos?
| Recurso | Actual | Confirmar |
|---|---|---|
| Aurora backup retention | 30 días | ✅ PCI-DSS typical |
| Aurora PITR | Habilitado | ✅ |
| Redis snapshots | Diarios, 30 días | ✅ |
| CDK stack drift detection | Habilitado (manual) | Configurar `cdk doctor` periódico |

### 9. ¿Monitoring y alertas base?
| Alerta | Métrica | Umbral | Acción |
|---|---|---|---|
| EKS cluster unhealthy | `cluster_status != ACTIVE` | Inmediato | PagerDuty |
| Node group capacity | `desired > max` | > 5 min | Slack |
| Redis CPU | `CPUUtilization > 80%` | > 10 min | Slack |
| Aurora CPU | `CPUUtilization > 80%` | > 10 min | Slack |
| Pod restarts | `container_restarts > 5` | > 15 min | PagerDuty |
| Circuit Breaker open | `resilience4j_circuitbreaker_state == 1` | Inmediato | PagerDuty |

**Decisión**: Implementar en CloudWatch Alarms + SNS → Slack/PagerDuty tras primer deploy.

### 10. ¿Cost control y budgets?
- **AWS Budgets**: Crear budget mensual con alerta 80% forecast, 100% actual
- **Cost Explorer**: Tag `Project=AndesRetail` en todos los recursos (CDK `Tags.of(stack).add()`)
- **Decisión**: Configurar tras primer deploy (conocer baseline real)

### 11. ¿Disaster Recovery / Failover región?
- **Actual**: Single region `us-east-1`, Multi-AZ dentro de región
- **RTO/RPO objetivo**: RTO < 4h, RPO < 1h (snapshots diarios Aurora/Redis)
- **Futuro (Fase 2)**: Active-passive en `us-west-2`
  - Route 53 failover routing
  - Aurora Global Database
  - ElastiCache Global Datastore
  - EKS cluster replica (ArgoCD sync)
- **Decisión**: Documentar runbook DR, programar test restore trimestral

### 11. ¿Compliance y auditoría?
| Requisito | Estado | Acción |
|---|---|---|
| PCI-DSS CDE isolation | Diseñado (subnets data sin IGW) | Validar post-deploy |
| KMS rotation | Habilitado (anual) | ✅ |
| Secrets rotation | Manual (SAP, Keycloak) | Automatizar con Lambda/EventBridge |
| Audit logging | CloudTrail habilitado (org) | Verificar log group |
| Config rules | `restricted-ssh`, `encrypted-volumes`, `rds-storage-encrypted` | Habilitar AWS Config rules |

---

## Respuestas registradas (auditoría)

| Pregunta | Respuesta | Fecha | Responsable |
|---|---|---|---|
| Environments provisioned | Pendiente (CDK sintetiza, deploy manual) | 2026-10-18 | AWS Platform |
| Network design | Validado en código (3 AZs, CDE aislado) | 2026-10-18 | AWS Platform |
| Secrets management | ExternalSecretsOperator + Secrets Manager | 2026-10-18 | AWS Platform |
| Cross-account | N/A (single account actual) | 2026-10-18 | AWS Platform |
| Account IDs | Placeholders (requiere cuentas reales) | 2026-10-18 | AWS Platform |
| TLS Certs | ACM pendiente (wildcard/SAN) | 2026-10-18 | AWS Platform |
| Domain/Route53 | Hosted zone pendiente | 2026-10-18 | AWS Platform |
| Sizing params | Graviton3, Multi-AZ, Serverless v2 | 2026-10-18 | AWS Platform |
| Backup/Retention | 30d Aurora, daily Redis, PITR | 2026-10-18 | AWS Platform |
| Monitoring/Alerts | CloudWatch + SNS (pendiente) | 2026-10-18 | AWS Platform |
| Cost Control | AWS Budgets (pendiente baseline) | 2026-10-18 | AWS Platform |
| DR/Failover | Fase 2 (us-west-2 active-passive) | 2026-10-18 | AWS Platform |
| Compliance | PCI-DSS CDE, KMS rotation, Config rules | 2026-10-18 | Compliance |