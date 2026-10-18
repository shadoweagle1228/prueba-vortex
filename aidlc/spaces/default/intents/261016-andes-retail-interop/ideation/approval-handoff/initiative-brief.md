## Sources

- [desc] Initial description del proyecto.
- [scope] Workflow-selected scope: `feature`.
- [Q4] Criterio de éxito MVP acordado.
- [Q6] Fuera de scope confirmado.
- [CA-01] Restricción absoluta: PCI-DSS y enmascaramiento PII.
- [CA-02] Restricción absoluta: backpressure en adaptadores legacy.
- [CA-03] Restricción absoluta: Clean Architecture en servicios backend.
- [F-Q1] Regulación Colombia Ley 1581/2012.
- [F-Q3] Restricción de costo — optimizar al máximo.
- [TF-stack] Stack: Kotlin + Spring Boot (backend) / Python + AWS CDK (IaC).
- [MR-Q3] API Gateway: híbrido AWS API GW + Kong.
- [MR-Q4] IdP: Keycloak OSS.

---

# Initiative Brief — Andes Retail Interoperability Platform

**Fecha:** Octubre 2026 | **Fase completada:** IDEATION | **Siguiente fase:** INCEPTION

---

## Problema

AndesRetail opera sistemas legacy (ERP, WMS, OMS, CRM) interconectados con integraciones
punto a punto frágiles que colapsan en picos de tráfico, generan inconsistencias de
datos entre canales y carecen de trazabilidad y seguridad homogénea. [desc][Q1]

---

## Solución Propuesta

Construir la **Plataforma de Integración y Eventos (PIE)**: una capa de interoperabilidad
sobre AWS EKS que expone APIs estandarizadas hacia canales externos, orquesta los flujos
críticos de negocio (stock, precio, órdenes, webhooks) y protege los sistemas legacy,
cumpliendo con PCI-DSS y la Ley 1581/2012 de Colombia desde el diseño inicial. [desc][CA-01][F-Q1]

**Seis pilares de la solución:**
1. APIs estándar versionadas por dominio (stock, precio, órdenes, webhooks) [desc]
2. Seguridad OAuth2/OIDC multi-tenant con scopes por canal [CA-01][Q8]
3. Protección de legados: Circuit Breaker + Bulkhead + Cache Redis en todos los adaptadores [CA-02]
4. Event-Driven Architecture con EventBridge para propagación asíncrona de estado [desc]
5. Clean Architecture en todos los servicios backend (Kotlin + Spring Boot) [CA-03][TF-stack]
6. Infraestructura como código con Python + AWS CDK, optimizada para costo [TF-stack][F-Q3]

---

## Criterios de Éxito del MVP (semanas 1-6)

| Criterio | Métrica | Estado |
|----------|---------|--------|
| Canal digital integrado end-to-end | 1 canal en producción | Confirmado [Q4] |
| Latencia stock/precio | p95 < 300ms | Confirmado [desc] |
| Disponibilidad APIs externas | 99.9% | Confirmado [desc] |
| Absorción de picos | 10x sin degradar legados | Confirmado [Q4] |
| Trazabilidad | 100% transacciones con correlation-id | Confirmado [desc] |
| PII en logs | Cero campos sensibles en claro | Mandatado [CA-01] |

---

## Stack Tecnológico Acordado

| Capa | Tecnología | Decisión |
|------|-----------|---------|
| Servicios backend | **Kotlin + Spring Boot** | Equipo Vortexbird ya usa Kotlin [TF-stack] |
| Infraestructura como código | **Python + AWS CDK** | Preferencia equipo Vortexbird [TF-stack] |
| API Gateway (edge público) | **AWS API Gateway REST** | Zero-ops, integrado con WAF [MR-Q3] |
| API Gateway (interno EKS) | **Kong Ingress Controller (OSS)** | Multi-tenant, sin licencia [MR-Q3] |
| IdP OAuth2/OIDC | **Keycloak OSS** | Sin costo de licencia [MR-Q4][F-Q3] |
| Bus de eventos | **Amazon EventBridge** | Zero-ops, nativo AWS [desc] |
| Cache | **ElastiCache Redis** | TLS + KMS, cluster mode [CA-01] |
| Base de datos | **RDS Aurora PostgreSQL Serverless v2** | Serverless para optimizar costo [F-Q3] |

---

## Restricciones Absolutas (no negociables en ningún stage posterior)

| ID | Restricción | Aplica a |
|----|------------|---------|
| CA-01 | PCI-DSS: CDE segmentado, PAN fuera de la PIE, PII enmascarado en logs | Toda la plataforma |
| CA-02 | Backpressure: CB + Bulkhead + Rate Limit en todos los adaptadores legacy | Stock, WMS, OMS, CRM Adapters |
| CA-03 | Clean Architecture: domain → application → infrastructure en cada servicio | Todos los servicios Kotlin |

---

## Fuera de Scope — Esta Entrega

- Arquitectura multi-región [Q6]
- Integración de todos los marketplaces (uno piloto en MVP) [Q6]
- Certificación PCI-DSS formal con QSA [Q6]
- Refactoring de sistemas legacy [CA-02]
- Frontend / UX del e-commerce [desc]

---

## Equipo y Modelo de Trabajo

**Equipo:** Vortexbird (4 personas: Arquitecto Senior, Backend Dev Senior, DevOps/Platform, QA 50%) [F-Q2]
**Trabajo:** Trunk-based development, squash merge a main, feature branches < 2 días [memory:M1]
**Decisiones:** Compartidas entre Vortexbird y equipo técnico de AndesRetail [Q5]
**Costo AWS estimado:** ~$275-295/mes en MVP [F-Q3]

---

## Bloqueadores Activos (resolver en semana 1)

| Bloqueador | Acción requerida |
|-----------|-----------------|
| I-01: Conectividad on-premise → AWS desconocida | Discovery técnico con IT de AndesRetail |
| I-02: APIs del ERP desconocidas | Sesión técnica con equipo ERP |
| D-06: Canal piloto del MVP no definido | Decisión del PO de AndesRetail |

---

## Próximos Pasos — Fase INCEPTION

Con la aprobación de este brief, la fase de Inception arranca con:
1. **Practices Discovery** — definir prácticas de equipo (CI/CD, testing, code review)
2. **Requirements Analysis** — requerimientos funcionales y no funcionales detallados
3. **Domain Design** — bounded contexts, entidades, agregados y contratos
4. **User Stories** — historias de usuario con criterios de aceptación
5. **Contract Design** — OpenAPI specs de las APIs v1
6. **NFR Requirements** — SLOs, RTO/RPO, compliance detallado
7. **Delivery Planning** — sprints, Bolts, secuencia de entrega

---

## Assumptions & Open Questions

- [assumption] Este brief es el documento de referencia que se presenta al CTO y PO de AndesRetail para aprobación formal antes de iniciar Inception. Si hay cambios de scope o restricciones después de esta aprobación, deben documentarse como ADRs.
