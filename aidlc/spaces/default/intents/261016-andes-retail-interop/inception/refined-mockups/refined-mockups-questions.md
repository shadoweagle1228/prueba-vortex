## Sources

- [desc] Proyecto API/backend sin UI de usuario final.
- [FR-01] a [FR-12] del requirements.md.
- [TF-stack] Kotlin + Spring Boot.

---

## RM2-Q1. Confirmación de naturaleza del proyecto (API/backend)

Este proyecto es una plataforma de APIs sin UI de usuario final. Los artefactos
de Refined Mockups son diagramas de interacción de sistema, contratos de API,
arquitectura de capas Clean Architecture, y estándares de diseño.

[Answer]: Confirmado — proyecto API/backend. Artefactos de refined mockups son diagramas técnicos de sistema y contratos de API pre-OpenAPI.

---

## RM2-Q2. Estándar de precios en centavos (integer)

El interaction-spec define precios como integer en centavos (COP) para evitar
errores de punto flotante.

[Answer]: Confirmado — precios en centavos. Ejemplo: $999.00 COP = 99900 (integer).

---

## Assumptions & Open Questions

- [assumption] La especificación OpenAPI completa (YAML con schemas, ejemplos y validaciones) se produce en el Contract Design stage — este artefacto es la especificación pre-formal que sirve de insumo para ese stage.
