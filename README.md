# Andes Retail — Interoperabilidad

Capa de integración entre los sistemas legacy (ERP SAP, WMS, OMS, CRM) y los
canales digitales de Andes Retail, desplegada sobre AWS EKS con enfoque
Security-First y Clean Architecture.

## Contenido del repositorio

| Ruta | Descripción |
|---|---|
| `services/stock-price-adapter/` | Microservicio Kotlin + Spring Boot que expone consultas de stock (Walking Skeleton). |
| `infra/` | Infraestructura como código (AWS CDK, Python): red, datos y cómputo. |
| `deploy/k8s/` | Manifiestos Kubernetes (Deployment, Service, HPA, NetworkPolicy, PDB). |
| `.github/workflows/ci.yml` | Pipeline de CI: lint, tests, umbral de cobertura y build de imagen. |
| `aidlc/` | Registro del ciclo de desarrollo AI-DLC (estado, diseño, trazabilidad). |

## Servicio `stock-price-adapter`

Arquitectura en capas (Clean Architecture) con la regla de dependencia
verificada por ArchUnit:

```
web  ──▶ application ──▶ domain ◀── infrastructure
```

- **domain**: modelos (`StockLevel`, `StockReservation`, VOs) y puertos
  (`StockCachePort`, `ErpStockPort`). Sin dependencias de framework.
- **application**: caso de uso `GetStockUseCase` / `GetStockService`.
- **infrastructure**: adaptadores Redis (patrón fresh/stale) y SAP (WebClient +
  Resilience4j CircuitBreaker/Bulkhead) con un mapper (ACL).
- **web**: API REST `/v1/inventory/stock/{sku}`, OAuth2 JWT con scope
  `stock:read`, errores RFC 7807 y filtro de correlación.

### Requisitos

- JDK 21 (el wrapper fija Gradle 8.7 y el toolchain a Java 21).

### Compilar y probar

```bash
./gradlew ktlintCheck test jacocoTestCoverageVerification
```

Empaquetar:

```bash
./gradlew :services:stock-price-adapter:bootJar
java -jar services/stock-price-adapter/build/libs/stock-price-adapter-0.1.0.jar
```

### Contrato de la API

```
GET /v1/inventory/stock/{sku}?warehouseId=W01
Authorization: Bearer <jwt con scope stock:read>

200 OK
{
  "sku": "ABC-123",
  "warehouseId": "W01",
  "quantityOnHand": 100,
  "quantityReserved": 10,
  "quantityAvailable": 90,
  "source": "ERP",
  "timestamp": "2026-10-18T00:00:00Z",
  "etag": "..."
}
```

Códigos: `400` (petición inválida), `401` (sin JWT), `403` (sin scope),
`503` (ERP no disponible), `500` (error inesperado). Todos los errores usan
`application/problem+json` e incluyen `correlationId`.

### Resiliencia

- **Circuit breaker** `sapStockCircuitBreaker`: abre tras 50 % de fallos con
  ventana de 20 llamadas y mínimo 10; reintenta a los 15 s.
- **Bulkhead** `sapStockBulkhead`: 40 llamadas concurrentes como máximo.
- **Caché**: clave fresca (TTL 60 s) y obsoleta (TTL 3600 s) para servir el
  último valor conocido si el ERP cae.

### Health

`/actuator/health/liveness`, `/actuator/health/readiness`,
`/actuator/prometheus`, `/actuator/health` (incluye el estado del circuit breaker).

## Infraestructura (AWS CDK)

```bash
cd infra
python -m venv .venv && . .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cdk synth
cdk deploy --all
```

- `NetworkStack`: VPC multi-AZ (pública / privada con egreso / aislada) + flow
  logs.
- `DataStack`: ElastiCache Redis Multi-AZ y Aurora MySQL Serverless v2 cifrada
  con KMS.
- `ComputeStack`: clúster EKS 1.29 con node group Spot ARM64 (Graviton).

## Despliegue Kubernetes

```bash
kubectl apply -k deploy/k8s
```

El namespace aplica la política `restricted` de Pod Security Admission; el
Deployment corre como usuario sin privilegios, con root filesystem de solo
lectura, sin escalada de privilegios y con NetworkPolicy de egreso restringido.
