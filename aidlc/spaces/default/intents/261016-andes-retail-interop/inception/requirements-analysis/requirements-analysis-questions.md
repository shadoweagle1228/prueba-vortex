## Sources

- [desc] Initial description del proyecto.
- [scope] Workflow-selected scope: `feature`.
- [Q4] Del intent: criterio de éxito MVP.
- [CA-01] Restricción absoluta: PCI-DSS y enmascaramiento PII.
- [CA-02] Restricción absoluta: backpressure en adaptadores legacy.
- [CA-03] Restricción absoluta: Clean Architecture en servicios backend.
- [TF-stack] Stack: Kotlin + Spring Boot / Python CDK.

---

## RA-Q1. Los requerimientos se derivan de los artefactos de ideation

Los requerimientos funcionales y no funcionales se derivan de:
- intent-statement.md (problema, criterios de éxito, scope)
- scope-document.md (APIs definidas, bloques del backlog)
- feasibility/constraint-register.md (restricciones absolutas CA-01/CA-02/CA-03)
- El brief del cliente (requerimientos funcionales y NFRs explícitos)

[Answer]: Requerimientos generados a partir de las fuentes anteriores sin preguntas adicionales al usuario — el contexto es suficiente.

---

## Assumptions & Open Questions

- [assumption] Los IDs de requerimientos siguen el formato FR-N (funcionales) y NFR-N (no funcionales) para mantener trazabilidad estable entre stages.
- [assumption] Cada requerimiento tiene un criterio de aceptación verificable (pass/fail) según la guardrail de inception "Requirements must be testable and verifiable".
