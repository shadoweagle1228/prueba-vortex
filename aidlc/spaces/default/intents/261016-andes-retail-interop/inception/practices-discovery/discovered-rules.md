## Mandated

- ALWAYS escribir el test (JUnit 5) antes del código de producción en los servicios de dominio (TDD cycle: Red → Green → Refactor). (affirmed 2026-10-16)
- ALWAYS ejecutar ktlint en el pre-commit hook y en CI antes de merge; un formato incorrecto bloquea el merge. (affirmed 2026-10-16)
- ALWAYS mantener la cobertura de líneas >= 80% en servicios Kotlin antes de merge a main; CI verifica este umbral. (affirmed 2026-10-16)
- ALWAYS implementar el walking skeleton (slice `GET /v1/inventory/stock/{sku}` end-to-end con CB y cache) antes de construir los servicios de dominio adicionales. (affirmed 2026-10-16)
- ALWAYS requerir aprobación manual de Tech Lead + PO de AndesRetail para deploys a producción. (affirmed 2026-10-16)
- ALWAYS verificar la Dependency Rule de Clean Architecture con ArchUnit en los tests de arquitectura de cada servicio. (affirmed 2026-10-16)

## Forbidden

- NEVER mergear un PR con tests rojos — un test fallido bloquea el merge sin excepción. (affirmed 2026-10-16)
- NEVER hacer deploy a producción sin aprobación manual explícita del Tech Lead y el PO de AndesRetail. (affirmed 2026-10-16)
- NEVER escribir código de producción antes de tener un test que falle en los servicios de dominio (aplica TDD). (affirmed 2026-10-16)
- NEVER crear ramas de larga duración (> 2 días) — las feature branches se integran a main en máximo 1-2 días. (affirmed 2026-10-16)
