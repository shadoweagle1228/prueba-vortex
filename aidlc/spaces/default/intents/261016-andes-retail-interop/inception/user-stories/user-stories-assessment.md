## Sources

- [desc] Initial description del proyecto.
- [scope] Intent backlog.
- [FR-01] a [FR-12] del requirements.md.

---

## User Stories Assessment

### Cobertura de Requerimientos

| Requerimiento | Historia | Estado |
|--------------|---------|--------|
| FR-01 stock | US-01 | ✅ Cubierto |
| FR-02 precio | US-02 | ✅ Cubierto |
| FR-03 crear orden | US-03 | ✅ Cubierto |
| FR-04 idempotencia | US-04 | ✅ Cubierto |
| FR-05 estado orden | US-05 | ✅ Cubierto |
| FR-06 compensación Saga | US-03 (escenario 3) | ✅ Cubierto |
| FR-07 webhook orden | US-06 | ✅ Cubierto |
| FR-08 webhook pago | US-07 | ✅ Cubierto |
| FR-09 webhook fulfillment | — | ⏳ Diferido a mes 3 (WMS Adapter) |
| FR-10 reintento DLQ | US-06 (escenario 2) | ✅ Cubierto |
| FR-11 OAuth2 multi-tenant | US-08 | ✅ Cubierto |
| FR-12 onboarding canal | US-09 | ✅ Cubierto |

### Restricciones Absolutas en Historias

| Restricción | Historia | Escenario |
|------------|---------|----------|
| CA-01 PII en logs | US-10 | Escenarios 1 y 2 |
| CA-01 token de pago (no PAN) | US-03 | Escenario 1, criterio de log |
| CA-01 aislamiento tenant | US-02 esc.3, US-05 esc.2, US-08 esc.3 | ✅ |
| CA-02 CB en Stock Adapter | US-01 | Escenario 3 |
| CA-02 compensación Saga | US-03 | Escenario 3 |
| CA-03 Clean Architecture | Implícito en US-03/04 (idempotencia, Saga) | ✅ |

### Independencia y Testeabilidad

Todas las historias son independientemente testables. US-03 depende conceptualmente de US-01 (necesita stock disponible) pero los tests pueden usar mocks del Stock Adapter para aislar el comportamiento.

---

## Assumptions & Open Questions

- [assumption] FR-09 y las historias de fulfillment se agregan al backlog de mes 3 cuando el WMS Adapter esté en scope.
- [assumption] Los escenarios de las historias son la base para los tests de aceptación que se escribirán en TDD durante construction (ciclo Red → Green → Refactor).
