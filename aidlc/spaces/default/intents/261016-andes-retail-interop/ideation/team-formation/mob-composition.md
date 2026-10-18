## Sources

- [F-Q2] Equipo Vortexbird construye todo.
- [scope] Workflow-selected scope: `feature`.
- [CA-03] Clean Architecture — decisiones de dominio requieren colaboración arquitecto + dev.

---

## Mob Composition — Colaboración por Bloque

### Modelo de Trabajo por Bloque

Para este proyecto, el equipo de Vortexbird opera en parejas o tríos según el bloque,
sin mob programming formal (equipo de 4 personas no justifica el overhead). [F-Q2]

| Bloque | Modo | Composición |
|--------|------|-------------|
| F-00 Discovery | Colaborativo | Arquitecto + representante AndesRetail |
| F-01 a F-04 Fundaciones IaC | Pairing | Arquitecto + DevOps |
| S-01 a S-05 Seguridad | Pairing | Arquitecto + DevOps |
| SP-01 a SP-06 Stock/Precio Adapter | Pairing | Backend Dev + Arquitecto (revisión Clean Architecture) |
| O-01 a O-06 Order Service | Pairing | Backend Dev + Arquitecto (diseño Saga) |
| W-01 a W-04 Webhooks | Solo + revisión | Backend Dev, revisado por Arquitecto |
| OB-01 a OB-04 Observabilidad | Solo + revisión | DevOps, revisado por Arquitecto |
| QA / Load testing | Solo + revisión | QA, revisado por Backend Dev |

### Puntos de Sincronización Obligatoria (Whole Team)

Los siguientes momentos requieren presencia de todo el equipo:

1. **Kick-off del proyecto** (día 1) — alineación del scope, las restricciones absolutas y el RAID log.
2. **Resultado del discovery técnico** (fin semana 1) — decisión sobre conectividad on-premise y APIs del ERP.
3. **Revisión de arquitectura del Order Service + Saga** (inicio semana 4) — el flujo más complejo del MVP.
4. **Demo del MVP** (fin semana 6) — validación end-to-end con AndesRetail.

---

## Assumptions & Open Questions

- [assumption] El mob-composition puede evolucionar durante el proyecto. Si emergen decisiones de alto impacto (cambio de stack, bloqueo de ERP), se convoca al equipo completo sin esperar la sincronización programada.
