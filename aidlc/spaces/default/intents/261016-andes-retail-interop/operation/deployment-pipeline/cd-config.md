# CD Configuration — `skeleton-stock-query`

## Herramienta CD

**AWS CDK + GitHub Actions** (despliegue manual/on-demand via `cdk deploy`, promoción automática a staging tras CI verde)

## Referencias upstream

| Artefacto | Ubicación | Qué aporta |
|---|---|---|
| `ci-config` | `construction/ci-pipeline/ci-config.md` | Pipeline CI (lint, test, coverage, docker build) |
| `quality-gates` | `construction/ci-pipeline/quality-gates.md` | Gates bloqueantes (cobertura ≥80%, ArchUnit, contrato C-01) |
| `infrastructure-specification` | `construction/skeleton-stock-query/infrastructure-design/infrastructure-specification.md` | Specs EKS, Redis, Aurora, Kong, Keycloak, NFR-04/06/11/12 |
| `cicd-pipeline` | `construction/ci-pipeline/ci-config.md` + `quality-gates.md` | Pipeline CI/CD integrado |

## Pipeline de despliegue

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   CI        │────▶│  Staging    │────▶│  Approval   │────▶│  Prod       │
│  (GitHub    │     │  (auto)     │     │  (manual)   │     │  (manual)   │
│  Actions)   │     │             │     │             │     │             │
└─────────────┘     └─────────────┘     └─────────────┘     └─────────────┘
                           │                    │                    │
                    cdk deploy --profile    Revisión +          cdk deploy --profile
                    staging              aprobación           prod
```

## Cuentas AWS

| Entorno | Cuenta ID | Perfil AWS | Región | VPC |
|---|---|---|---|---|
| **staging** | `111122223333` | `andes-staging` | `us-east-1` | `vpc-0aaa...` |
| **prod** | `444455556666` | `andes-prod` | `us-east-1` | `vpc-0bbb...` |

> **Nota**: Los IDs de cuenta son placeholders. Configurar en `~/.aws/config` y `~/.aws/credentials` o vía AWS SSO.

## Stacks CDK (orden de deploy)

```bash
# 1. Network (VPC, subnets, security groups)
cdk deploy AndesRetailNetworkStack --profile <profile>

# 2. Security (KMS, Secrets Manager)
cdk deploy AndesRetailSecurityStack --profile <profile>

# 3. Data (Redis ElastiCache, Aurora PostgreSQL)
cdk deploy AndesRetailDataStack --profile <profile>

# 4. Compute (EKS, Kong, Keycloak, ESO)
cdk deploy AndesRetailComputeStack --profile <profile>
```

**Dependencias**: Network → Security → Data → Compute (CDK las resuelve automáticamente vía `addDependency`).

## Parámetros de stacks (context CDK)

```json
// cdk.json (context)
{
  "env": {
    "account": "111122223333",
    "region": "us-east-1"
  },
  "stackPrefix": "AndesRetail",
  "vpcCidr": "10.0.0.0/16",
  "redisNodeType": "cache.t4g.medium",
  "auroraEngineVersion": "16.1",
  "eksVersion": "1.29",
  "nodeGroupInstanceType": "c7g.medium",
  "nodeGroupMinSize": 2,
  "nodeGroupMaxSize": 6,
  "kongChartVersion": "2.33.0",
  "keycloakChartVersion": "24.0.0",
  "externalSecretsChartVersion": "0.9.0"
}
```

## GitHub Actions CD (workflow sugerido)

```yaml
# .github/workflows/cd.yml (no implementado aún - futuro)
name: CD

on:
  workflow_run:
    workflows: ["CI"]
    types: [completed]
    branches: [main]

jobs:
  deploy-staging:
    if: ${{ github.event.workflow_run.conclusion == 'success' }}
    runs-on: ubuntu-latest
    timeout-minutes: 45
    permissions:
      id-token: write
      contents: read
    steps:
      - uses: actions/checkout@v4
      - name: Configure AWS credentials (staging)
        uses: aws-actions/configure-aws-credentials@v4
        with:
          role-to-assume: arn:aws:iam::111122223333:role/GitHubActionsDeployRole
          aws-region: us-east-1
      - name: Setup Python + CDK
        uses: actions/setup-python@v5
        with:
          python-version: "3.11"
      - name: Install CDK deps
        run: pip install -r infra/requirements.txt
      - name: CDK Deploy Staging
        run: |
          cd infra
          cdk deploy --all --require-approval never --profile staging
    environment: staging
```

## Autenticación AWS en CI (OIDC)

```hcl
# IAM Role para GitHub Actions (staging)
resource "aws_iam_role" "github_actions_deploy" {
  name = "GitHubActionsDeployRole"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect = "Allow"
      Principal = { Federated = "arn:aws:iam::111122223333:oidc-provider/token.actions.githubusercontent.com" }
      Action = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringLike = {
          "token.actions.githubusercontent.com:sub" = "repo:andes-retail/*:ref:refs/heads/main"
        }
      }
    }]
  })
}

# Permisos mínimos: CloudFormation, EC2, EKS, ElastiCache, RDS, IAM (pass role), KMS, SecretsManager
```

## Variables de entorno CD

| Variable | Descripción | Staging | Prod |
|---|---|---|---|
| `CDK_DEFAULT_ACCOUNT` | Cuenta AWS | `111122223333` | `444455556666` |
| `CDK_DEFAULT_REGION` | Región | `us-east-1` | `us-east-1` |
| `STACK_PREFIX` | Prefijo stacks | `AndesRetail` | `AndesRetail` |

## Secretos necesarios en GitHub

| Secret | Descripción | Staging | Prod |
|---|---|---|---|
| `AWS_ROLE_ARN_STAGING` | ARN role OIDC staging | ✅ | — |
| `AWS_ROLE_ARN_PROD` | ARN role OIDC prod | — | ✅ |
| `DOCKER_REGISTRY_TOKEN` | Token ghcr.io/ECR | ✅ | ✅ |

## Comandos manuales (fallback)

```bash
# Perfil staging
export AWS_PROFILE=andes-staging
cd infra && pip install -r requirements.txt && cdk deploy --all

# Perfil prod (requiere aprobación manual)
export AWS_PROFILE=andes-prod
cd infra && cdk deploy --all --require-approval broadening
```

## Verificación post-deploy

```bash
# 1. Verificar stacks
aws cloudformation list-stacks --stack-status-filter CREATE_COMPLETE UPDATE_COMPLETE \
  --query "StackSummaries[?starts_with(StackName, 'AndesRetail')].StackName"

# 2. Verificar EKS
aws eks describe-cluster --name andes-retail-staging --query 'cluster.status'
kubectl --context arn:aws:eks:us-east-1:111122223333:cluster/andes-retail-staging get nodes

# 3. Verificar Kong + Keycloak
kubectl -n kong get pods,svc,ingress
kubectl -n keycloak get pods,svc

# 4. Verificar app deployment
kubectl -n andes-retail get deployment stock-price-adapter
kubectl -n andes-retail get pods -l app.kubernetes.io/name=stock-price-adapter
```