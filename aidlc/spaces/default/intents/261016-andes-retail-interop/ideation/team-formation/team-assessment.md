## Sources

- [F-Q2] Equipo Vortexbird construye todo.
- [F-Q3] Restricción de costo — equipo mínimo viable.
- [scope] Workflow-selected scope: `feature`.
- [CA-01] PCI-DSS — seguridad como capacidad obligatoria del equipo.
- [CA-03] Clean Architecture — capacidad técnica obligatoria.

---

## Team Assessment

### Estructura del Equipo Mínimo (MVP)

El feasibility-assessment determinó el equipo mínimo necesario para cumplir el
MVP en 4-6 semanas. [F-Q2][F-Q3]

| Rol | Persona | Dedicación | Responsabilidades principales |
|-----|---------|-----------|------------------------------|
| Arquitecto/a Senior (Tech Lead) | Vortexbird | 100% | Diseño de arquitectura, ADRs, IaC (CDK Python), decisiones de seguridad, revisión de código, integración con AndesRetail |
| Backend Developer Senior | Vortexbird | 100% | Order Service + Stock Adapter en Kotlin/Spring Boot (Clean Architecture), contratos de API, tests |
| DevOps / Platform Engineer | Vortexbird | 100% | EKS, CI/CD, Kong, Keycloak, Prometheus/Grafana, Secrets Manager, pipeline de seguridad |
| QA Engineer | Vortexbird | 50% (desde semana 3) | Pruebas de integración, load testing (k6), validación de criterios de verificación del backlog |

**Nota:** Con un equipo de 3 personas a tiempo completo + 1 QA parcial, el MVP
es alcanzable en 6 semanas. Con 2 personas, se extiende a 10-12 semanas. [F-Q3]

---

### Riesgos de Equipo

| Riesgo | Probabilidad | Mitigación |
|--------|-------------|-----------|
| Single point of failure en conocimiento de EKS/seguridad | Media | Pair programming en los componentes de infraestructura; documentación como IaC |
| Curva de aprendizaje de Clean Architecture para el equipo dev | Media | Sesión de onboarding de arquitectura en semana 1; ArchUnit en tests desde el inicio |
| Vacaciones o ausencias durante el sprint crítico (semana 4-5) | Baja | Identificar y bloquear calendarios antes de iniciar |

---

### Modelo de Trabajo

Siguiendo la política de la organización (trunk-based development, squash merge a main): [memory:M1]

- **Ramas:** feature branches de vida corta (1-2 días máximo)
- **Integración:** PRs a main con revisión de al menos 1 peer
- **Despliegue:** deploy automático a dev en merge; deploy a staging con aprobación manual
- **Cadencia:** standup diario (15min), revisión técnica semanal con AndesRetail

---

## Assumptions & Open Questions

- [assumption] La composición del equipo es la mínima viable para el MVP. Si Vortexbird tiene más personas disponibles, se puede paralelizar bloques del backlog (ej. Bloque 1 Seguridad y Bloque 2 Stock/Precio en paralelo desde semana 3).
- [assumption] El Arquitecto Senior asume el rol de Tech Lead y punto de contacto técnico principal con AndesRetail. Si este rol está separado, debe aclararse la cadena de decisión.
