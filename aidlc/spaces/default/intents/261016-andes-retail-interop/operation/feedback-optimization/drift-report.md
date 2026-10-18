# Drift Detection Report — `skeleton-stock-query`

## Referencias upstream

| Artefacto | Ubicación | Qué aporta |
|---|---|---|
| `infrastructure-specification` | `construction/skeleton-stock-query/infrastructure-design/infrastructure-specification.md` | Specs infra declaradas |
| `deployment-log.md` | `operation/deployment-execution/deployment-log.md` | Estado deploy real |
| `infrastructure-design` | `construction/skeleton-stock-query/infrastructure-design/` | Stacks CDK, specs |

---

## Resumen de Detección de Drift

| Categoría | Drifts Detectados | Críticos | Advertencias | Estado |
|---|---|---|---|---|
| **Infraestructura (CDK/AWS)** | ⏳ Pendiente deploy | 0 | 0 | ⏳ Sin deploy real |
| **Kubernetes (K8s)** | ⏳ Pendiente deploy | 0 | 0 | ⏳ Sin deploy real |
| **Configuración App (ConfigMap/Secrets)** | ⏳ Pendiente deploy | 0 | 0 | ⏳ Sin deploy real |
| **Seguridad (IAM, KMS, SGs)** | ⏳ Pendiente deploy | 0 | 0 | ⏳ Sin deploy real |
| **Observabilidad (Dashboards, Alarms)** | ⏳ Pendiente deploy | 0 | 0 | ⏳ Sin deploy real |

> **Nota**: Este reporte se ejecuta contra entornos reales desplegados. Actualmente el entorno staging/prod no ha sido desplegado (`cdk deploy` pendiente). El reporte se ejecutará automáticamente post-deploy y en schedule semanal.

---

## Metodología de Detección

### Herramientas Utilizadas
| Herramienta | Scope | Frecuencia | Output |
|---|---|---|---|
| **AWS Config** | Recursos AWS (EKS, RDS, ElastiCache, IAM, KMS, VPC) | Continuo + Snapshots diarios | Configuration History, Compliance Rules |
| **CDK Diff** | Stacks CDK vs Deployed | Pre-deploy + Semanal | `cdk diff` output |
| **Kubernetes (kubectl/kubediff)** | Deployments, ConfigMaps, Secrets, NetworkPolicies | Pre-deploy + Diario | `kubectl diff` / `kubediff` |
| **Helm Diff** | Releases Kong, Keycloak, ESO | Pre-upgrade + Semanal | `helm diff` |
| **GitOps (ArgoCD/Flux)** | Si implementado | Continuo | Sync Status, Diff |

### Reglas de Compliance (AWS Config Rules - Obligatorias)
| Regla | Descripción | Severidad | Remediation |
|---|---|---|---|
| `restricted-ssh` | SG no permite SSH 0.0.0.0/0 | CRITICAL | Auto-remediate: revocar regla |
| `encrypted-volumes` | EBS volumes encriptados | CRITICAL | Auto: encriptar |
| `rds-storage-encrypted` | RDS/Aurora encriptado | CRITICAL | Auto: no remediable (recrear) |
| `elasticache-redis-cluster-auto-minor-version-upgrade` | Habilitado | WARNING | Manual |
| `elb-tls-termination` | ALB solo TLS 1.2+ | CRITICAL | Manual |
| `iam-password-policy` | Política contraseñas IAM | WARNING | Auto |
| `kms-key-rotation-enabled` | Rotación CMK anual | WARNING | Auto: habilitar |
| `s3-bucket-ssl-requests-only` | Solo HTTPS | CRITICAL | Auto: bucket policy |
| `vpc-default-security-group-closed` | Default SG sin ingress | WARNING | Auto: revocar reglas |
| `vpc-flow-logs-enabled` | VPC Flow Logs habilitados | WARNING | Auto: crear flow log |

---

## Plantilla de Reporte de Drift (Post-Deploy)

### 1. Infraestructura AWS (CDK vs Real)
| Stack | Recurso | Propiedad | Esperado (CDK) | Actual (AWS) | Drift | Acción |
|---|---|---|---|---|---|---|
| NetworkStack | VPC | CIDR | `10.0.0.0/16` | ⏳ | ⏳ | — |
| NetworkStack | Subnets | Count | 9 (3 pub, 3 app, 3 data) | ⏳ | ⏳ | — |
| SecurityStack | KMS Key | Rotation | Enabled | ⏳ | ⏳ | — |
| DataStack | Redis | Node Type | `cache.t4g.medium` | ⏳ | ⏳ | — |
| DataStack | Redis | Multi-AZ | Enabled | ⏳ | ⏳ | — |
| DataStack | Aurora | Engine Version | `16.1` | ⏳ | ⏳ | — |
| ComputeStack | EKS | Version | `1.29` | ⏳ | ⏳ | — |
| ComputeStack | NodeGroup | Instance Type | `c7g.medium` | ⏳ | ⏳ | — |

### 2. Kubernetes (Deployed vs Git)
| Namespace | Recurso | Campo | Esperado (Git) | Actual (Cluster) | Drift | Acción |
|---|---|---|---|---|---|---|
| andes-retail | Deployment | Image Tag | `v0.1.0` | ⏳ | ⏳ | — |
| andes-retail | Deployment | Replicas | 2 (HPA min) | ⏳ | ⏳ | — |
| andes-retail | Deployment | Resources | CPU 500m/1000m, Mem 512Mi/1Gi | ⏳ | ⏳ | — |
| andes-retail | ConfigMap | Data | 10 keys (Redis, OIDC, TTLs) | ⏳ | ⏳ | — |
| andes-retail | Secret | Keys | `sap-credentials`, `metrics-token` | ⏳ | ⏳ | — |
| andes-retail | NetworkPolicy | Ingress | `kong`, `monitoring` namespaces | ⏳ | ⏳ | — |
| andes-retail | NetworkPolicy | Egress | DNS, Kong, Redis, SAP CIDRs | ⏳ | ⏳ | — |
| andes-retail | ServiceMonitor | Bearer Secret | `stock-price-adapter-metrics-token` | ⏳ | ⏳ | — |
| kong | KongPlugin | Rate Limit | 100 req/s, window 1s | ⏳ | ⏳ | — |
| kong | KongConsumer | Count | 3 (ecommerce, tienda, operador) | ⏳ | ⏳ | — |
| keycloak | Deployment | Replicas | 2 | ⏳ | ⏳ | — |
| monitoring | ServiceMonitor | Namespace | `andes-retail` | ⏳ | ⏳ | — |

### 3. Helm Releases (Kong, Keycloak, ESO)
| Release | Namespace | Chart | Version Esperada | Version Actual | Drift | Acción |
|---|---|---|---|---|---|---|
| kong | kong | kong/kong | 2.33.0 | ⏳ | ⏳ | — |
| keycloak | keycloak | bitnami/keycloak | 24.0.0 | ⏳ | ⏳ | — |
| external-secrets | external-secrets | external-secrets/external-secrets | 0.9.0 | ⏳ | ⏳ | — |

### 4. Seguridad (IAM, KMS, SGs)
| Recurso | Propiedad | Esperado | Actual | Drift | Acción |
|---|---|---|---|---|---|
| CMK `andes-retail` | Rotation | Enabled (Annual) | ⏳ | ⏳ | — |
| Secret `andes-retail/sap-credentials` | Exists | True | ⏳ | ⏳ | — |
| Secret `andes-retail/keycloak-admin` | Exists | True | ⏳ | ⏳ | — |
| SG EKS Nodes | Ingress | Solo ALB/Kong SG | ⏳ | ⏳ | — |
| SG Redis | Ingress | Solo EKS Nodes SG (puerto 6379) | ⏳ | ⏳ | — |
| SG Aurora | Ingress | Solo Keycloak SG (puerto 5432) | ⏳ | ⏳ | — |
| SG ALB/Kong | Ingress | 0.0.0.0/0 puerto 443 | ⏳ | ⏳ | — |
| IAM Role `OnCallRole` | Permissions | `kubectl`, AWS read-only, secrets read | ⏳ | ⏳ | — |

### 5. Observabilidad (Dashboards, Alarms, Logs)
| Componente | Recurso | Esperado | Actual | Drift | Acción |
|---|---|---|---|---|---|
| CloudWatch Dashboard | `AndesRetail/StockAdapter/GoldenSignals` | Exists, 8 widgets | ⏳ | ⏳ | — |
| CloudWatch Dashboard | `AndesRetail/Infrastructure/Overview` | Exists, 10 widgets | ⏳ | ⏳ | — |
| CloudWatch Alarm | `CRIT-STOCK-CB-OPEN` | Enabled, SNS `andes-critical` | ⏳ | ⏳ | — |
| CloudWatch Alarm | `CRIT-STOCK-LATENCY-P95` | Enabled, SNS `andes-critical` | ⏳ | ⏳ | — |
| PrometheusRule | `stock-adapter-anomalies` | 6 rules, namespace `andes-retail` | ⏳ | ⏳ | — |
| Log Group | `/aws/eks/andes-retail/...` | Retention 30d, KMS encrypted | ⏳ | ⏳ | — |
| X-Ray Tracing | Enabled | Sampling 10%/100% | ⏳ | ⏳ | — |

---

## Automatización de Detección y Remediación

### Pipeline de Detección (Semanal Automático)
```yaml
# .github/workflows/drift-detection.yml
name: Weekly Drift Detection
on:
  schedule:
    - cron: '0 2 * * 1'  # Lunes 2 AM UTC
jobs:
  drift-detection:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: CDK Diff
        run: |
          cd infra
          pip install -r requirements.txt
          cdk diff --profile andes-staging --profile andes-prod > drift-cdk.txt
      - name: K8s Diff
        run: |
          kubectl config use-context arn:aws:eks:us-east-1:111122223333:cluster/andes-retail-staging
          kubectl diff -k deploy/ > drift-k8s-staging.txt || true
          kubectl config use-context arn:aws:eks:us-east-1:444455556666:cluster/andes-retail-prod
          kubectl diff -k deploy/ > drift-k8s-prod.txt || true
      - name: Helm Diff
        run: |
          helm diff upgrade kong kong/kong -n kong --version 2.33.0 > drift-helm-kong.txt || true
          helm diff upgrade keycloak bitnami/keycloak -n keycloak --version 24.0.0 > drift-helm-keycloak.txt || true
      - name: Upload Artifacts
        uses: actions/upload-artifact@v4
        with:
          name: drift-reports
          path: drift-*.txt
      - name: Notify on Drift
        if: always()
        run: |
          if grep -q "Diff" drift-*.txt; then
            echo "DRIFT DETECTED" | slack-webhook -c "#andes-alerts"
          fi
```

### Remediación Automática (AWS Config)
```json
{
  "ConfigRules": [
    {
      "ConfigRuleName": "restricted-ssh-auto-remediate",
      "Source": { "Owner": "AWS", "SourceIdentifier": "RESTRICTED_SSH" },
      "Automatic": true,
      "MaximumExecutionFrequency": "TwentyFour_Hours"
    },
    {
      "ConfigRuleName": "encrypted-volumes-auto-remediate",
      "Source": { "Owner": "AWS", "SourceIdentifier": "ENCRYPTED_VOLUMES" },
      "Automatic": true
    }
  ]
}
```

---

## Métricas de Calidad de Drift

| KPI | Target | Medición |
|---|---|---|
| **Drift Detection Time** | < 24h | Timestamp drift → detection |
| **Drift Remediation Time** | < 4h (CRIT), < 24h (WARN) | Detection → Remediated |
| **Drift Recurrence Rate** | 0% mismo drift en 30d | Recurring drifts / total |
| **Config Coverage** | 100% recursos críticos | Recursos con Config Rule / Total críticos |
| **Auto-Remediation Rate** | 100% para reglas con auto-remediate | Auto-remediated / Total remediable |

---

## Historial de Drifts (Post-Deploy)

| Fecha | Categoría | Recurso | Drift Descripción | Severidad | Resuelto | Acción Tomada |
|---|---|---|---|---|---|---|
| — | — | — | — | — | — | — |

> Se poblará tras primer deploy y ejecuciones semanales.

---

## Próximos Pasos

1. **Post-Staging Deploy**: Ejecutar `cdk diff`, `kubectl diff`, `helm diff` completos
2. **Habilitar AWS Config Rules** obligatorias en ambas cuentas
3. **Configurar Pipeline Drift Detection** semanal en GitHub Actions
4. **Integrar con Incident Response**: Drift CRITICAL → Auto-incidente SEV-2
3. **Dashboard Drift**: Añadir panel "Drift Status" a dashboard `AndesRetail/Infrastructure/Overview`