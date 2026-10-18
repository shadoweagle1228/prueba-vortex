## Sources

- [desc] Initial description del proyecto Andes Retail.
- [CA-01] PCI-DSS: segmentación CDE y cifrado KMS.
- [CA-02] Backpressure y resiliencia.
- [performance-design.md] Requerimientos de red y latencia.
- [security-design.md] Aislamiento de subredes e IRSA.
- [IDQ-01 a IDQ-03] Preguntas de diseño de infraestructura.

---

# Especificación de Infraestructura — `skeleton-stock-query` (Walking Skeleton)

Este documento especifica los recursos de nube en **AWS** aprovisionados mediante **AWS CDK en Python** para la infraestructura base y el despliegue del microservicio `stock-price-adapter`.

---

## 1. Topología y Recursos Compartidos (Shared Infrastructure)

| Componente | Tipo de Recurso AWS | Especificación / Tamaño | Segmentación / Zona | Propósito |
|---|---|---|---|---|
| **VPC Principal** | `AWS::EC2::VPC` | CIDR `10.100.0.0/16` | 2 AZs (`us-east-1a`, `1b`) | Red troncal de la PIE |
| **Subredes Públicas** | `AWS::EC2::Subnet` | `10.100.0.0/24`, `10.100.1.0/24` | Pública (IGW asociado) | ALBs perimetrales y NAT Gateways |
| **Subredes App** | `AWS::EC2::Subnet` | `10.100.10.0/22`, `10.100.14.0/22`| Privada (Ruta a NAT) | Nodos EKS y Kong Ingress |
| **Subredes Datos/CDE**| `AWS::EC2::Subnet` | `10.100.20.0/24`, `10.100.21.0/24`| Privada Aislada (No NAT) | Redis, Aurora y CDE [CA-01] |
| **KMS Key Principal** | `AWS::KMS::Key` | CMK con rotación anual | Global por región | Cifrado en reposo para Redis y Secretos |
| **VPC Endpoints** | `AWS::EC2::VPCEndpoint` | Interface Endpoints (Privatelink) | Subredes App y CDE | Acceso privado a SecretsManager y ECR |

---

## 2. Servicios de Cómputo y Plataforma (Compute & Ingress)

| Servicio | Recurso AWS / K8s | Configuración / Tipo de Instancia | Escala / Réplicas | SLA / Resiliencia |
|---|---|---|---|---|
| **Cluster EKS** | `AWS::EKS::Cluster` | Kubernetes 1.29 (API Server Privado) | Multi-AZ gestionado | SLA 99.95% |
| **NodeGroup App** | `AWS::EKS::Nodegroup` | EC2 `c7g.medium` (ARM64 Graviton3) | Min: 2, Max: 6 nodos | Auto-escalado Cluster Autoscaler |
| **Kong Ingress** | Helm Release | Kong Ingress Controller en EKS | 2 réplicas (Multi-AZ) | Rate limiting (100 req/s por canal) |
| **Keycloak IdP** | Helm Release | Keycloak 24 en namespace `identity` | 2 réplicas (Multi-AZ) | OIDC / OAuth2 JWT multi-tenant |
| **External Secrets**| Helm Release | External Secrets Operator (ESO) | 1 réplica por cluster | Sincronización continua de KMS |

---

## 3. Servicios de Datos y Persistencia (Data Layer)

| Base de Datos / Caché | Recurso AWS | Motor y Versión | Configuración de Nodos | Cifrado y Backup |
|---|---|---|---|---|
| **ElastiCache Redis** | `AWS::ElastiCache::ReplicationGroup` | Redis 7.1 (Cluster Mode Disabled) | 1 Primario + 1 Réplica Multi-AZ (`cache.t4g.medium`) | Cifrado en tránsito (TLS) y reposo (KMS) |
| **Aurora PostgreSQL** | `AWS::RDS::DBCluster` | PostgreSQL 16 (Serverless v2) | 0.5 a 4 ACUs (Auto-scaling) | Multi-AZ, KMS Storage, Backup 7 días |

---

## 4. Despliegue del Servicio (Workload Specification: `stock-price-adapter`)

| Atributo K8s | Valor de Configuración | Justificación |
|---|---|---|
| **Namespace** | `pie-inventory` | Aislamiento lógico de inventario y catálogo |
| **Réplicas Iniciales** | 2 pods | Despliegue Multi-AZ mínimo para alta disponibilidad |
| **HPA** | Min: 2, Max: 10 pods | Dispara con CPU > 70% |
| **Recursos (CPU)** | Request: `250m`, Limit: `1000m` | Optimizado para Graviton3 ARM64 |
| **Recursos (RAM)** | Request: `512Mi`, Limit: `1024Mi` | Suficiente para JVM 21 y buffers de red |
| **Health Probes** | Liveness & Readiness en `/actuator/health` | Desacoplado del ERP para evitar reinicios en cascada |
| **ServiceAccount** | `stock-price-adapter-sa` | Anotado con IAM Role IRSA para lectura de secretos |

---

## 5. Snippet Ilustrativo CDK Python (≤15 líneas)

```python
# Ilustrativo (≤15 líneas): Definición de Subredes Aisladas CDE y Redis en CDK
class DataStack(Stack):
    def __init__(self, scope: Construct, construct_id: str, vpc: ec2.IVpc, kms_key: kms.IKey, **kwargs):
        super().__init__(scope, construct_id, **kwargs)
        self.redis_subnet_group = elasticache.CfnSubnetGroup(
            self, "RedisSubnetGroup",
            description="Subredes de datos aisladas para Redis",
            subnet_ids=[s.subnet_id for s in vpc.isolated_subnets]
        )
        self.redis_cluster = elasticache.CfnReplicationGroup(
            self, "RedisCluster",
            replication_group_description="Redis Cache Multi-AZ",
            engine="redis", cache_node_type="cache.t4g.medium",
            num_cache_clusters=2, automatic_failover_enabled=True,
            transit_encryption_enabled=True, at_rest_encryption_enabled=True,
            kms_key_id=kms_key.key_id, cache_subnet_group_name=self.redis_subnet_group.ref
        )
```

---

## Assumptions & Open Questions

- [assumption] Los artefactos de CDK residen en el repositorio bajo la carpeta `infra/` y se ejecutan mediante `cdk synth` y `cdk deploy --all` con credenciales de AWS CLI autenticadas.
