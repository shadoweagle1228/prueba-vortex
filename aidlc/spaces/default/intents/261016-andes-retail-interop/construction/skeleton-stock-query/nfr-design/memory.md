<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
2026-10-17T21:34:00Z — El diseño de NFR tradujo los requerimientos a patrones concretos: Resilience4j declarativo en adaptadores de infraestructura, Spring Security 6 stateless con JWT, y pipeline dual en Redis con Lettuce.
2026-10-17T21:34:00Z — La estructura de paquetes en Kotlin separa rigurosamente domain, application e infrastructure, blindada con reglas automáticas de ArchUnit.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
2026-10-17T21:34:00Z — Se decidió aplicar Resilience4j exclusivamente en la capa de adaptadores de infraestructura para mantener el núcleo del dominio completamente puro y libre de frameworks, facilitando pruebas unitarias ultrarrápidas con MockK.
2026-10-17T21:34:00Z — Se implementó la propagación de Correlation-ID mediante MDC en un OncePerRequestFilter para asegurar que cualquier hilo generado por Spring herede automáticamente el contexto de logging sin código manual en los controladores.

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
