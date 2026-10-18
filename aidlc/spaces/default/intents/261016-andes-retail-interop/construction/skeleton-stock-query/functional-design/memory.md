<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
2026-10-17T21:26:00Z — El diseño funcional del walking skeleton se centró en la resiliencia y el desacoplamiento estricto del ERP mediante el patrón ACL y el Circuit Breaker con Resilience4j.
2026-10-17T21:26:00Z — Se definió un esquema de doble clave en Redis: clave principal con TTL de 60s (fresco) y clave secundaria de stale cache con TTL de 60m para contingencia ante caídas del enlace on-premise.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
2026-10-17T21:26:00Z — Se prefirió retornar datos ligeramente desactualizados de la caché (stale cache) con cabecera explícita X-Stock-Source=stale-cache en lugar de fallar de inmediato con error 503 cuando el ERP no responde, maximizando la disponibilidad en canales digitales sin engañar al consumidor.
2026-10-17T21:26:00Z — Se mantuvo el timeout del ERP en 250ms para que la latencia acumulada con el API Gateway se mantenga estrictamente por debajo del umbral de 300ms p95.

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
