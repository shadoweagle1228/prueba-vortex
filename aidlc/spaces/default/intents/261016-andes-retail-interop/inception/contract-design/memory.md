<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
2026-10-16T22:55:00Z — Se definió OpenAPI 3.0.3 para todas las interfaces síncronas REST tanto externas como internas pod-to-pod. Se eligió AsyncAPI 2.6 para documentar los eventos de EventBridge y los webhooks salientes hacia canales externos.
2026-10-16T22:55:00Z — Los montos monetarios se estandarizaron en centavos COP (`amountInCents`, integer int64) según ADR-01, evitando errores de punto flotante.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
2026-10-16T22:55:00Z — Se optó por comunicación síncrona HTTP interna para reservas y autorización de pago durante el flujo de orden en vez de coreografía 100% asíncrona, para mantener la latencia p95 end-to-end dentro del SLA de 2 segundos [NFR-02] y ofrecer respuesta inmediata al canal.
2026-10-16T22:55:00Z — Se utilizó RFC 7807 (Problem Details) de forma uniforme en todas las APIs de error para simplificar el manejo de excepciones y diagnóstico en clientes externos y monitoreo.

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
