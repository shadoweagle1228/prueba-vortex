## Sources

- [F-Q2] Equipo Vortexbird construye todo el proyecto.
- [scope] Workflow-selected scope: `feature`.
- [desc] Initial description del proyecto.
- [CA-03] Restricción absoluta: Clean Architecture en servicios backend.

---

## TF-Q1. Composición del equipo Vortexbird

El equipo que ejecuta el proyecto es de Vortexbird. [F-Q2]

[Answer]: Equipo Vortexbird — composición a definir. Se asume equipo mínimo de 3-4 personas
(Arquitecto Senior, Backend Dev, DevOps/Platform, QA parcial) según feasibility-assessment.md.

---

## TF-Q2. Capacidades críticas para el scope definido

Las capacidades más críticas según el backlog son:
- Clean Architecture + Kotlin + Spring Boot (Order Service, Stock Adapter) [CA-03]
- AWS EKS + CDK Python (infraestructura y plataforma)
- Security (OIDC/OAuth2, PCI-DSS, log sanitization) [CA-01]

[Answer]: Capacidades mapeadas al skill-matrix.md.

---

## Assumptions & Open Questions

- [assumption] Se asume que el Arquitecto Senior de Vortexbird lidera tanto el diseño arquitectónico como la toma de decisiones técnicas, y que el equipo de desarrollo tiene experiencia en JVM (Kotlin o Java) con Spring Boot.
- [assumption] Si el equipo real de Vortexbird difiere de la composición mínima del feasibility, este artefacto debe actualizarse antes del inicio de construction.
