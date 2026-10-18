## Sources

- [desc] Initial description del proyecto.
- [scope] Workflow-selected scope: `feature`.
- [CA-01] Restricción absoluta: PCI-DSS — segmentación CDE y enmascaramiento PII.
- [CA-02] Restricción absoluta: backpressure en todos los adaptadores hacia legados.
- [CA-03] Restricción absoluta: Clean Architecture en servicios backend.
- [Q4] Del intent: criterio de éxito MVP acordado.
- [Q6] Del intent: fuera de scope confirmado.

---

## SD-Q1. Confirmación de las tres restricciones absolutas incorporadas

El usuario mandató explícitamente las siguientes restricciones como no negociables para
todos los stages posteriores de diseño e implementación:

1. PCI-DSS — Segmentación CDE y enmascaramiento de PII [CA-01]
2. Backpressure en WMS/ERP mediante Circuit Breaker, Bulkhead y Rate Limiting [CA-02]
3. Clean Architecture en todos los servicios backend [CA-03]

[Answer]: Incorporadas como restricciones absolutas en scope-document.md y propagadas a intent-backlog.md. Aplican a todos los stages de inception, construction y operation.

---

## SD-Q2. Priorización del backlog MVP

El backlog MVP está organizado en 5 bloques secuenciales. La secuencia es:
Fundaciones → Seguridad → Stock/Precio → Orden → Webhooks+Observabilidad.

[Answer]: Secuencia aceptada por dependencias técnicas: no se puede construir Orden sin Stock/Precio, ni Stock/Precio sin la infraestructura de Fundaciones y Seguridad.

---

## Assumptions & Open Questions

- [assumption] La decisión de Colombia como jurisdicción (no Chile) fue incorporada en todos los artefactos de feasibility y scope. El constraint CR-01 a CR-06 del constraint-register.md refleja Ley 1581/2012 de Colombia.
- [assumption] El scope de mes 3 y mes 6 son orientativos. Se refinan al inicio de cada ciclo con base en lo aprendido en el MVP.
