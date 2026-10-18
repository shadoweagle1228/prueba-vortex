## Sources

- [desc] Initial description del proyecto Andes Retail.
- [CA-01] PCI-DSS: segmentación de red CDE y cifrado KMS.
- [CA-02] Backpressure y resiliencia.
- [performance-design.md] Requerimientos de red y latencia.
- [security-design.md] Aislamiento de subredes e IRSA.
- [scalability-design.md] HPA y despliegue multi-AZ.
- [tech-stack-decisions.md] AWS CDK Python, EKS Graviton3, Redis 7.

---

## IDQ-01. Arquitectura de Red y Topología de VPC

¿Cómo se estructuran las subredes de la VPC para cumplir con la segmentación PCI-DSS y alta disponibilidad?

- **VPC CIDR:** `10.100.0.0/16` desplegada en 2 Zonas de Disponibilidad (`us-east-1a`, `us-east-1b`).
- **Nivel 1 — Subredes Públicas (`10.100.0.0/24`, `10.100.1.0/24`):** Albergan los balanceadores de carga Application Load Balancers (ALB) y NAT Gateways redundantes.
- **Nivel 2 — Subredes Privadas de Aplicación (`10.100.10.0/22`, `10.100.14.0/22`):** Albergan los nodos administrados de EKS (instancias c7g Graviton3) y Kong Ingress. Salida a internet solo vía NAT Gateway.
- **Nivel 3 — Subredes Privadas de Datos / CDE (`10.100.20.0/24`, `10.100.21.0/24`):** Subredes estrictamente aisladas sin salida a internet (no route to NAT/IGW). Albergan ElastiCache Redis, RDS Aurora Serverless v2 y los pods de procesamiento CDE [CA-01].

[Answer]: VPC de 3 niveles con subredes CDE/Datos completamente aisladas sin rutas de internet.

---

## IDQ-02. Gestión de Identidad y Roles en EKS (IRSA)

¿Cómo se asignan permisos de AWS a los microservicios sin usar llaves estáticas?

- Implementación de **IAM Roles for Service Accounts (IRSA)** mediante el proveedor OIDC del cluster EKS.
- El ServiceAccount `stock-price-adapter-sa` asume un rol IAM específico con políticas de mínimo privilegio:
  - `secretsmanager:GetSecretValue` restringido al ARN del secreto de SAP y Redis.
  - Cero permisos de administración de AWS.

[Answer]: Uso exclusivo de IRSA (IAM Roles for Service Accounts) con permisos de mínimo privilegio.

---

## IDQ-03. Estrategia de Stacks en AWS CDK (Python)

¿Cómo se modularizan los stacks de CDK para garantizar despliegues independientes y reproducibilidad por entorno?

- `pie-network-stack`: VPC, subnets, NAT Gateways, VPC Endpoints y Security Groups.
- `pie-security-stack`: Claves maestras KMS, políticas base IAM y configuración de Secrets Manager.
- `pie-data-stack`: Cluster ElastiCache Redis Multi-AZ y Aurora Serverless v2.
- `pie-compute-stack`: Cluster EKS, NodeGroups Graviton3, y Addons (CoreDNS, Kube-Proxy, VPC-CNI, EBS CSI).
- `pie-app-stack`: Ingress Kong, Helm charts de External Secrets Operator y despliegue del pod `stock-price-adapter`.

[Answer]: 5 stacks modulares desacoplados en AWS CDK Python parametrizados por entorno (dev, staging, prod).

---

## Assumptions & Open Questions

- [assumption] Los stacks de CDK se sintetizan a CloudFormation y se despliegan en la región `us-east-1` cumpliendo con la optimización de costo y latencia hacia Colombia.
