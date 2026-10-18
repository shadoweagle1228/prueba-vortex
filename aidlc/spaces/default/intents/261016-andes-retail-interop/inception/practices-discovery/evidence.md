## Evidence

### Fuentes inspeccionadas

- `aidlc/spaces/default/memory/org.md` — prácticas por defecto de la organización (trunk-based, squash-merge, test-after como default por scope `feature`).
- `aidlc/spaces/default/intents/261016-andes-retail-interop/ideation/team-formation/team-assessment.md` — composición del equipo, modelo de trabajo confirmado.
- `aidlc/spaces/default/intents/261016-andes-retail-interop/ideation/team-formation/skill-matrix.md` — stack tecnológico: Kotlin + JUnit 5 + Spring Boot / Python CDK.
- Entrevista de practices discovery (preguntas PD-Q1 a PD-Q5).

### Decisiones de la entrevista

| Pregunta | Respuesta | Fuente |
|----------|-----------|--------|
| PD-Q1 — Walking skeleton | A — Sí, construir slice end-to-end primero | Entrevista |
| PD-Q2 — Metodología testing | B — TDD (test primero) | Entrevista |
| PD-Q3 — Herramientas testing Kotlin | A — JUnit 5 + Mockito | Entrevista |
| PD-Q4 — Estrategia deployment | B — Auto a dev+staging, manual a prod | Entrevista |
| PD-Q5 — Code style Kotlin | A — ktlint | Entrevista |

### Desviaciones respecto al default org.md

- **Testing**: org.md default para scope `feature` es test-after. El equipo eligió **TDD** — práctica más estricta. Aceptada como override del default.
- **Metodología**: el `Methodology` field en team.md será `tdd`, no `test-after`.

### Incertidumbres sin resolver

- La herramienta de CD (CodePipeline vs. GitHub Actions) se define en construction al configurar el pipeline de CI/CD. No bloquea las prácticas afirmadas aquí.
- detekt como linter adicional se evalúa en construction — ktlint es suficiente para el arranque.
