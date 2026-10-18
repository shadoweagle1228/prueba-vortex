<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
2026-10-17T21:30:00Z — Los requerimientos NFR para el walking skeleton se concentraron en fijar umbrales cuantitativos innegociables para latencia (p95 <= 300ms), resiliencia hacia SAP y seguridad perimetral.
2026-10-17T21:30:00Z — Se confirmó la selección de JDK 21 con Kotlin 1.9 sobre arquitectura ARM64 Graviton para optimizar la relación rendimiento/costo en EKS.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
2026-10-17T21:30:00Z — Se optó por un límite de concurrencia de 20 hilos en el Bulkhead del ERP, aceptando degradar peticiones excedentes a stale cache con tal de blindar al ERP de cualquier riesgo de sobrecarga o caída durante picos comerciales.
2026-10-17T21:30:00Z — Se eligió Lettuce como cliente Redis por su modelo no bloqueante reactivo y eficiente manejo de pools frente al modelo tradicional de Jedis.

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
