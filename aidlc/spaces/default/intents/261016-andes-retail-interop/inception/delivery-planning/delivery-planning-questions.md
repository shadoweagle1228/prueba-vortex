## Sources

- [desc] Initial description del proyecto Andes Retail.
- [scope] Scope document y intent backlog.
- [team-practices] Prácticas afirmadas en team.md y project.md (TDD, trunk-based, walking skeleton, cobertura >= 80%).
- [U1] a [U8] del units.md de units-generation.
- [C-01] a [C-09] del contract-summary.md de contract-design.
- [mob-composition] Modelo de trabajo y composición de equipo de team-formation.

---

## DP-Q1. Estrategia de Secuenciamiento Inicial

¿Qué debemos construir primero: las partes más riesgosas, las de mayor valor comercial, o un corte delgado de punta a punta (walking skeleton) que demuestre que toda la arquitectura se sostiene?

- **Estrategia Elegida:** **Walking Skeleton Primero**. Un corte vertical delgado de punta a punta (*walking skeleton*: el primer bloque de trabajo que recorre toda la arquitectura hasta funcionar de forma integrada) sobre la consulta de existencias (`GET /v1/inventory/stock/{sku}`).
- **Justificación:** Tal como fue afirmado en las prácticas del equipo (`team.md`), el mayor riesgo técnico es la integración con el ERP on-premise, la latencia de red, la autenticación JWT y el comportamiento del Circuit Breaker con caché Redis. Validar este corte delgado de extremo a extremo despeja la viabilidad arquitectónica antes de construir el Order Service y la pasarela de pagos.

[Answer]: Walking skeleton primero (slice de consulta de stock con Circuit Breaker y Redis), seguido secuencialmente por valor de negocio y mitigación de riesgos.

---

## DP-Q2. Granularidad y Definición de Bolts

¿Qué tamaño debe tener cada Bolt (un ciclo de construcción sobre una porción del trabajo que culmina en algo ejecutable y verificable)?

- **Estructura:** Cada Bolt mapea 1:1 a las Unidades de Trabajo identificadas en `units-generation` para garantizar trazabilidad estricta y permitir verificación independiente (8 Bolts en total).
- **Criterio de Entrega:** Cada Bolt cuenta con su propia Definición de Terminado (DoD), hipótesis de confianza medible y demostración ejecutable esperada.

[Answer]: 8 Bolts secuenciales correspondientes 1:1 a las unidades de trabajo del Unit DAG.

---

## DP-Q3. Ejecución de Construcción y Asignación de Equipo

¿Cómo se ejecutarán los Bolts durante la fase de construcción y cómo se asigna el equipo?

- **Modo de Ejecución:** `serial` con iteración `unit-major` (cada Bolt recorre su ciclo de diseño, codificación TDD y pruebas antes de pasar al siguiente), respetando la directriz de trunk-based development con squash-merge en `main` [team.md].
- **Asignación:** Equipo Vortexbird en modalidad pairing/colaborativo según el mapa de `mob-composition.md`. Para propósitos del motor AI-DLC en sesión guiada, la ejecución se coordina de manera centralizada (`solo`).

[Answer]: Ejecución serial unit-major coordinada en sesión, con pairing técnico definido por especialidad de bloque.

---

## DP-Q4. Dependencias Externas y Bloqueos

¿Existen dependencias fuera del control del equipo de desarrollo que puedan retrasar la entrega?

- **Dependencias Críticas:**
  - Acceso a APIs y ambiente de prueba del ERP SAP de Andes Retail (bloquea Bolt 1). Mitigación: Mock server de ERP de alta fidelidad para pruebas locales y de integración.
  - Credenciales y sandbox del PSP (Pasarela de Pagos) (bloquea Bolt 4). Mitigación: Mock PSP con soporte de decline codes para pruebas de Saga.
  - Definición del canal digital piloto (E-commerce) y endpoints de webhook (bloquea Bolt 5).
  - Aprobaciones de despliegue a producción por Tech Lead y PO de AndesRetail [project.md].

[Answer]: Mitigación mediante mocks de ERP y PSP en entornos locales/CI para no bloquear el desarrollo; coordinación temprana de credenciales de sandbox.

---

## Assumptions & Open Questions

- [assumption] La verificación del walking skeleton en Bolt 1 es el hito fundamental que valida el cluster EKS, la VPC segmentada, Keycloak, Redis y la conectividad antes de proceder con el flujo transaccional de órdenes.
- [assumption] Las prácticas de TDD con JUnit 5 y cobertura mínima de 80% aplican a todos los Bolts de servicios de backend.
