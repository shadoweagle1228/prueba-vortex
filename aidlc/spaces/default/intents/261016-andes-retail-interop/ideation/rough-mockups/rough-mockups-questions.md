## Sources

- [desc] Initial description del proyecto.
- [scope] Workflow-selected scope: `feature`.
- [CA-01] PCI-DSS — segmentación CDE.
- [CA-02] Backpressure hacia legados.
- [TF-stack] Kotlin + Spring Boot / Python CDK.

---

## RM-Q1. ¿Hay interfaces de usuario en el scope?

Este proyecto es una plataforma de APIs backend (PIE). No hay UI de usuario final
en el scope. [desc][scope]

[Answer]: No hay UI — se producen diagramas de contexto del sistema y flujos de interacción clave entre sistemas.

---

## RM-Q2. ¿Cuáles son las interacciones de sistema más críticas a visualizar?

[Answer]: Los tres flujos críticos son: (1) consulta de stock/precio por canal externo, (2) creación de orden end-to-end con Saga, (3) flujo de autenticación OAuth2/OIDC multi-tenant.

---

## Assumptions & Open Questions

- [assumption] Los diagramas producidos en este stage son la representación visual de la arquitectura que guía el diseño en las fases de inception y construction. No son wireframes de UI sino diagramas de sistema (context diagrams, sequence diagrams en ASCII/texto estructurado).
