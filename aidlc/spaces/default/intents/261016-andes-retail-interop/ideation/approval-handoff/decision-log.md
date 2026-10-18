## Sources

- Todos los artefactos producidos en la fase IDEATION.

---

## Decision Log — Fase IDEATION

Registro de todas las decisiones clave tomadas durante la fase de Ideation.
Cada decisión tiene un ID estable para ser referenciada en stages posteriores.

---

| ID | Decisión | Alternativas consideradas | Razón | Stage | Fecha |
|----|----------|--------------------------|-------|-------|-------|
| D-01 | **Stack backend: Kotlin + Spring Boot** | Java + Spring Boot, Python FastAPI, Node.js TypeScript | El equipo de Vortexbird ya usa Kotlin. Kotlin es null-safe, conciso para Clean Architecture, interoperable con Java/Spring. | team-formation | Oct 2026 |
| D-02 | **IaC: Python + AWS CDK** | Terraform, Pulumi, CloudFormation YAML | El equipo de Vortexbird prefiere Python para IaC. CDK permite construir con el mismo lenguaje del equipo. | team-formation | Oct 2026 |
| D-03 | **API Gateway: híbrido AWS API GW (edge) + Kong (interno)** | Solo AWS API GW, Solo Kong, Traefik, Apigee | AWS API GW: zero-ops en el edge, integrado con WAF y Route53. Kong OSS: multi-tenant con plugins custom sin licencia en el interior. La combinación optimiza costo y operación. | market-research | Oct 2026 |
| D-04 | **IdP: Keycloak OSS** | AWS Cognito, Auth0, Okta | Costo de licencia cero vs. $800-2000/mes de Auth0/Okta. Keycloak soporta el modelo multi-tenant complejo con scopes custom. AWS Cognito tiene limitaciones de customización para B2B. | market-research | Oct 2026 |
| D-05 | **Bus de eventos: Amazon EventBridge** | Apache Kafka (MSK), RabbitMQ self-hosted | Volumen de eventos < 5M/mes en MVP → costo < $5/mes. Zero-ops vs. la complejidad operacional de MSK. Kafka se revisará en mes 6 si el volumen escala. | market-research | Oct 2026 |
| D-06 | **Regulación aplicable: Colombia (Ley 1581/2012)** | Chile (Ley 19.628) | AndesRetail opera en Colombia, no en Chile. La ley colombiana aplica obligaciones de consentimiento, finalidad, registro SIC y seguridad del dato. | feasibility | Oct 2026 |
| D-07 | **Restricción absoluta CA-01: PCI-DSS + enmascaramiento PII** | PCI-DSS como objetivo no bloqueante | El usuario mandató explícitamente PCI-DSS (CDE, tokenización, PII en logs) como restricción no negociable en todos los stages. | scope-definition | Oct 2026 |
| D-08 | **Restricción absoluta CA-02: backpressure en todos los adaptadores legacy** | Backpressure como feature posterior | El usuario mandató explícitamente backpressure (CB + Bulkhead + Rate Limit) en ERP, WMS, OMS, CRM como restricción no negociable. | scope-definition | Oct 2026 |
| D-09 | **Restricción absoluta CA-03: Clean Architecture en todos los servicios** | Arquitectura en capas clásica, hexagonal | El usuario mandató explícitamente Clean Architecture (domain/application/infrastructure) como restricción no negociable. | scope-definition | Oct 2026 |
| D-10 | **MVP scope: un canal piloto end-to-end en 4-6 semanas** | MVP más amplio (3 canales), MVP más estrecho (solo stock/precio) | Equilibrio entre valor demostrable (un canal completo) y tiempo de entrega realista con el equipo disponible. El patrón se replica para canales adicionales en mes 3+. | scope-definition | Oct 2026 |
| D-11 | **Fuera de scope MVP: multi-región, cert. PCI formal, todos los marketplaces** | Incluir multi-región desde el inicio | Multi-AZ es suficiente para 99.9% SLA en MVP. La certificación PCI es un proceso de AndesRetail, no de Vortexbird. El patrón de onboarding de canal se valida con uno antes de escalar. | scope-definition | Oct 2026 |
| D-12 | **Costo AWS estimado MVP: ~$275-295/mes** | No estimado | Restricción de costo es máxima optimización. Se usarán instancias ARM Graviton, Aurora Serverless v2, servicios managed/serverless donde el delta justifique el trade-off operacional. | feasibility | Oct 2026 |

---

## Supuestos Activos al Cierre de IDEATION

Estos supuestos pasan a la fase de Inception como inputs al Requirements Analysis y al RAID log:

| ID | Supuesto | Riesgo si es falso |
|----|----------|-------------------|
| A-01 | El ERP tiene alguna interfaz para consultar stock en tiempo (cuasi-)real | R-02 del RAID: se necesitan 2-3 semanas adicionales para construir la capa de exposición |
| A-02 | La pasarela de pagos tokeniza — el PAN nunca llega a sistemas de AndesRetail | Si es falso, el scope PCI se expande significativamente |
| A-03 | Existe conectividad de red on-premise → AWS (Direct Connect o VPN) | R-01 del RAID: bloqueador crítico para el Stock Adapter |
| A-04 | El equipo de AndesRetail puede participar en sesiones técnicas de discovery en semanas 1-2 | Sin esto el discovery se extiende y el roadmap se desplaza |
| A-05 | La cuenta AWS existente tiene permisos para crear EKS, RDS, ElastiCache, EventBridge, Secrets Manager | Verificar en semana 1 |

---

## Assumptions & Open Questions

- [assumption] El decision log se actualiza en cada stage de Inception y Construction cuando se toman decisiones de arquitectura adicionales. El formato `D-NN` mantiene continuidad entre fases.
