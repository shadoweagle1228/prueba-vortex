## Sources

- [desc] Initial description del proyecto.
- [scope] Workflow-selected scope: `feature`.
- [Q4] Del intent: criterio de éxito MVP — canal end-to-end + eliminación P2P + soporte 10x.
- [Q6] Del intent: excluidos multi-región, todos los marketplaces, certificación PCI formal.
- [CA-01] Restricción absoluta: PCI-DSS — segmentación CDE y enmascaramiento PII.
- [CA-02] Restricción absoluta: backpressure en todos los adaptadores hacia legados.
- [CA-03] Restricción absoluta: Clean Architecture en todos los servicios backend.
- [F-Q1] Regulación Colombia Ley 1581/2012.
- [F-Q3] Restricción de costo: optimizar al máximo.
- [MR-Q3] API Gateway: arquitectura híbrida AWS API GW + Kong.
- [MR-Q4] IdP: Keycloak OSS.

---

## Scope Document — Andes Retail Interoperability Platform

### Declaración de Scope

Construir la **Plataforma de Integración y Eventos (PIE) de AndesRetail**: una capa
de interoperabilidad sobre AWS que expone APIs estandarizadas hacia canales externos,
orquesta los flujos de negocio críticos (orden, stock, precio, webhooks) y protege
los sistemas legacy de sobrecarga, cumpliendo con PCI-DSS y la Ley 1581/2012 de
Colombia desde el primer día de diseño. [desc][CA-01][F-Q1]

---

### Restricciones Absolutas (no negociables en ningún stage)

Estas restricciones fueron mandatadas explícitamente y aplican a toda decisión de
diseño, implementación y operación sin excepción. [CA-01][CA-02][CA-03]

1. **PCI-DSS — Segmentación CDE y enmascaramiento de PII:** El CDE está delimitado
   por subnets privadas aisladas en AWS. El PAN nunca viaja fuera del CDE. Los datos
   PII (nombre, email, teléfono, dirección) se enmascaran en logs en todos los
   servicios. El Order Service solo maneja tokens de pago, nunca PANs. [CA-01]

2. **Backpressure en adaptadores hacia legados:** Los sistemas ERP, WMS, OMS y CRM
   nunca reciben más carga de la que pueden manejar. Todo adaptador hacia un sistema
   legacy implementa obligatoriamente: Rate Limiting (por canal), Circuit Breaker
   (Resilience4j), Bulkhead (thread pool aislado) y Timeout fail-closed. [CA-02]

3. **Clean Architecture en todos los servicios backend:** La estructura de cada
   microservicio es `domain → application → infrastructure`. Las entidades y casos
   de uso del dominio no importan frameworks ni adaptadores. Las dependencias apuntan
   siempre hacia adentro (Dependency Rule de Robert C. Martin). [CA-03]

---

### En Scope — MVP (semanas 1-6)

#### Infraestructura y Plataforma
- VPC con subnets privadas, CDE segmentado, VPC Endpoints para servicios AWS [CA-01]
- EKS Cluster (instancias ARM Graviton para optimizar costo) con API Server privado [F-Q3]
- AWS API Gateway (REST) como edge público + Kong Ingress Controller interno [MR-Q3]
- Keycloak OSS en EKS para OIDC/OAuth2 multi-tenant [MR-Q4]
- ElastiCache Redis (cache de stock y catálogo, TTL configurable)
- RDS Aurora PostgreSQL Serverless v2 (Order Service + Keycloak) [F-Q3]
- Amazon EventBridge (buses de dominio por bounded context)
- AWS Secrets Manager + External Secrets Operator (rotación automática)
- CI/CD pipeline básico (CodePipeline + CodeBuild o GitHub Actions)

#### Servicios de Dominio (Clean Architecture) [CA-03]
- **Order Service:** Crear orden, consultar estado, idempotencia, publicación de eventos, log sanitization PII [CA-01][CA-03]
- **Stock/Price Adapter (ACL → ERP):** Consultar stock por SKU/canal, consultar precio por SKU/canal/cliente, Cache Redis, Circuit Breaker, Bulkhead [CA-02][CA-03]
- **Payment Service:** Autorización de pago con token (nunca PAN), compensación Saga [CA-01][CA-03]
- **Notification Service:** Webhooks salientes (orden confirmada, pago aprobado/rechazado, despacho iniciado, entrega confirmada)

#### APIs Externas (contrato v1)
- `GET /v1/inventory/stock/{sku}` — stock por SKU/tienda/canal
- `GET /v1/catalog/price/{sku}` — precio por SKU/canal/cliente
- `POST /v1/orders` — crear orden (con idempotencia)
- `GET /v1/orders/{orderId}` — consultar estado de orden
- Webhooks/Eventos salientes: `order.confirmed`, `payment.approved`, `payment.rejected`, `shipment.started`, `delivery.confirmed`

#### Seguridad y Cumplimiento
- OAuth2/OIDC con scopes y roles por canal (multi-tenant) [CA-01]
- Log sanitization middleware — enmascaramiento PII en todos los servicios [CA-01][F-Q1]
- Cifrado en tránsito (TLS 1.3) y en reposo (KMS) para RDS, Redis, SQS, Secrets Manager [CA-01]
- Scan automatizado de PII en CI/CD (regex patterns) [CA-01][F-Q1]

#### Resiliencia
- Rate Limiting por canal en Kong [CA-02]
- Circuit Breaker + Bulkhead en todos los adaptadores legacy [CA-02]
- DLQ en SQS para eventos fallidos con reintentos exponenciales
- Cache Redis como barrera primaria ante fallo/latencia del ERP [CA-02]

#### Observabilidad MVP
- Logs estructurados JSON con correlation-id en todos los servicios
- Prometheus + Grafana (métricas clave)
- CloudWatch Alarms para Circuit Breaker abierto y DLQ con mensajes
- 10 métricas clave + 6 alertas críticas

---

### En Scope — Mes 3

- Integración WMS Adapter (fulfillment asíncrono via EventBridge) [CA-02][CA-03]
- Integración CRM Adapter (sincronización de cliente) [CA-02][CA-03]
- Onboarding de segundo canal digital (marketplace piloto)
- Chaos Engineering con AWS Fault Injection Simulator
- Developer Portal (Kong Dev Portal) para documentación de APIs externas
- SAST en CI/CD (Semgrep con reglas PCI-DSS y Ley 1581)
- Pruebas de carga al 5x volumen normal

---

### En Scope — Mes 6

- Onboarding de todos los canales digitales definidos por AndesRetail
- Multi-AZ con failover automático probado y documentado
- SLOs documentados con Error Budget y alertas de quema de budget
- Canary deployments con Argo Rollouts
- DR drill documentado (RTO/RPO verificados)
- Evaluación de migración a arquitectura multi-región si el negocio lo justifica

---

### Fuera de Scope — Esta Entrega [Q6]

| Ítem | Justificación |
|------|--------------|
| Arquitectura multi-región | Costo y complejidad no justificados en MVP; multi-AZ es suficiente para 99.9% SLA [Q6] |
| Integración de todos los marketplaces | Se hace un canal piloto para validar el patrón; los demás se onboardean en mes 3+ [Q6] |
| Certificación PCI-DSS formal con QSA | El diseño es PCI-compatible desde el inicio; la certificación es un proceso separado de AndesRetail [Q6][CA-01] |
| Refactoring de sistemas legacy (ERP, WMS, OMS, CRM) | Los legados no se modifican — la capa de integración los adapta con ACLs [CA-02] |
| Frontend / UX del e-commerce | La PIE es una capa de APIs; el frontend es responsabilidad de AndesRetail [desc] |
| Machine Learning / personalización | Fuera del alcance de la capa de integración [desc] |

---

## Assumptions & Open Questions

- [assumption] El scope "un canal piloto en MVP" implica que AndesRetail debe decidir qué canal es el piloto antes de la semana 2. Sin esta decisión, el MVP no puede validarse end-to-end. Esta es la dependencia D-06 del RAID log.
- [assumption] El scope de backpressure [CA-02] aplica a todos los adaptadores, incluyendo el OMS y CRM Adapter aunque no estén en el MVP. Su diseño base (con Circuit Breaker) se entrega en mes 3, no en el MVP.
