## Sources

- [Q5] Autoridad de decisión: compartida entre Vortexbird y el equipo técnico de AndesRetail.
- [Q4] Criterio de éxito del MVP acordado.
- [Q8] Modelo multi-tenant por canal.
- [desc] Initial description del proyecto.

---

## Stakeholder Map

| Stakeholder | Rol | Interés principal | Autoridad | Source |
|-------------|-----|-------------------|-----------|--------|
| CTO / Arquitecto Jefe de AndesRetail | Decision-maker técnico | Que la arquitectura sea sostenible, no genere dependencia de vendor y el equipo interno pueda operarla | Aprobación de decisiones de arquitectura | [Q5] |
| Product Owner / Gerente Digital de AndesRetail | Decision-maker de negocio | Que el MVP habilite nuevos canales y reduzca incidentes en promos lo antes posible | Aprobación del roadmap y prioridades de negocio | [Q4] |
| Equipo técnico de AndesRetail (ERP, WMS, OMS, CRM) | Influenciador técnico | Que sus sistemas no se vean afectados, que las integraciones sean estables y que el equipo pueda mantenerlas | Proporciona acceso a APIs legacy y valida contratos | [Q2][Q5] |
| Equipo de Operaciones de AndesRetail (SRE/Infra) | Co-owner operacional | Observabilidad, runbooks claros, que la plataforma sea operable sin conocimiento profundo de la capa de integración | Opera la plataforma post-entrega | [Q5][desc] |
| Partners / Marketplaces externos | Consumidor de APIs | APIs estables, contratos versionados, notificaciones de eventos confiables y SLAs documentados | No tienen autoridad interna; sí pueden escalar si los SLAs no se cumplen | [Q8][desc] |
| Equipo Vortexbird (Arquitecto Senior + equipo) | Proveedor de la solución | Entregar una arquitectura de calidad que refleje las mejores prácticas del estado del arte | Autonomía técnica en el diseño, sujeto a aprobación de AndesRetail | [Q5] |
| Equipo de Seguridad / Compliance de AndesRetail | Guardián de cumplimiento | Que el manejo de PII y datos de pago cumpla con regulaciones aplicables (ley de protección de datos, PCI-DSS en scope reducido) | Puede vetar decisiones que expandan el scope PCI o expongan PII | [Q3][desc] |

---

## Decision-Makers vs. Influencers

**Decision-makers (aprueban):**
- CTO / Arquitecto Jefe de AndesRetail — decisiones de arquitectura [Q5]
- Product Owner de AndesRetail — roadmap y prioridades [Q4]

**Influenciadores (informan y validan):**
- Equipo técnico de cada sistema legacy — contratos de integración [Q2]
- Equipo de Seguridad — controles de cumplimiento [Q3]
- Partners externos — feedback sobre contratos de API [Q8]

---

## Communication Requirements

| Audiencia | Cadencia | Canal | Contenido | Source |
|-----------|----------|-------|-----------|--------|
| CTO + Arquitecto AndesRetail | Semanal | Reunión técnica + ADRs escritos | Decisiones de arquitectura, trade-offs, riesgos abiertos | [Q5] |
| Product Owner | Semanal | Demo + resumen ejecutivo | Avance del MVP, qué está disponible, qué bloqueadores hay | [Q4] |
| Equipo técnico legacy | Bi-semanal | Sesión técnica | Contratos de API, dependencias, fechas de discovery | [Q2] |
| Equipo Operaciones | Al entregar cada componente | Documentación + runbook | Guías de operación, alertas, runbooks de nivel 1 | [desc] |
| Partners externos | Al publicar APIs | Developer portal + email | Contratos de API, changelogs, SLAs, tiempos de deprecación | [Q8] |

---

## Assumptions & Open Questions

- [assumption] Se asume que el CTO de AndesRetail es el decisor técnico final. Si existe un Comité de Arquitectura formal, el proceso de aprobación de ADRs debe ajustarse. [Q5]
- [assumption] Los partners/marketplaces externos no participan en el diseño de los contratos de API — se les consulta durante la fase de inception para validar los contratos propuestos, pero no co-diseñan. [Q8]
