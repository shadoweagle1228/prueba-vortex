# Infraestructura como código — Andes Retail

Stacks de AWS CDK (Python) que aprovisionan la plataforma del ecosistema de
interoperabilidad.

## Stacks

| Stack | Recursos |
|---|---|
| `AndesRetailNetworkStack` | VPC multi-AZ (públicas, privadas con egreso y aisladas), NAT gateways y flow logs a CloudWatch Logs. |
| `AndesRetailSecurityStack` | Clave KMS con rotación anual (cifrado en reposo de Redis, Aurora y secretos) y secreto de credenciales de SAP. |
| `AndesRetailDataStack` | ElastiCache Redis 7.1 Multi-AZ `cache.t4g.medium` (cifrado en tránsito y reposo con la CMK) y Aurora **PostgreSQL 16** Serverless v2 (0.5–4 ACU) en subredes aisladas. |
| `AndesRetailComputeStack` | Clúster EKS 1.29 (sin capacidad por defecto) + node group Graviton3 `c7g.medium` (2–6 nodos), addon `metrics-server`, Kong Ingress Controller (rate limiting por canal, NFR-11), Keycloak 24 sobre la Aurora PostgreSQL (FR-11/US-08) y External Secrets Operator. |

## Uso

```bash
python -m venv .venv
# Linux/macOS
source .venv/bin/activate
# Windows (PowerShell)
# .venv\Scripts\Activate.ps1

pip install -r requirements.txt

export CDK_DEFAULT_ACCOUNT=<account-id>
export CDK_DEFAULT_REGION=us-east-1

cdk synth
cdk diff
cdk deploy --all
```

`python app.py` también sintetiza el ensamblado en `cdk.out/` sin necesidad de
credenciales AWS, lo que sirve como verificación rápida de los stacks.

## Notas de seguridad

- El tráfico de datos nunca atraviesa subredes públicas: Redis y Aurora se
  ubican en subredes aisladas y el tráfico de egreso sale por NAT.
- El cifrado en reposo es obligatorio (misma CMK para Redis, Aurora y secretos)
  y el tránsito a Redis es TLS.
- Solo el security group de los nodos de EKS puede alcanzar Aurora en el puerto
  5432, y se hace por el puerto de identidad de Keycloak.
- No se crean usuarios ni bases de datos por defecto: el aprovisionamiento del
  esquema (base `keycloak`) es responsabilidad del pipeline de migraciones.
- El secreto de SAP llega a los pods vía External Secrets Operator, nunca como
  variable en claro en el manifiesto.
