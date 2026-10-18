<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
2026-10-16T22:35:00Z — El walking skeleton (U1) incluye infraestructura base (VPC, EKS, RDS, Redis, Kong, Keycloak) porque sin ella no se puede demostrar el slice end-to-end. Las unidades posteriores reutilizan esta infraestructura base sin redefinirla.
2026-10-16T22:35:00Z — El Catalog Context (precios, U2) comparte el mismo servicio (Stock/Price Adapter) que el Inventory Context en el MVP. Es una unidad separada porque tiene su propia lógica de dominio (precios promocionales, segmentación), pero operacionalmente es una extensión del adapter de U1.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
2026-10-16T22:35:00Z — Se optó por 8 unidades en vez de granularizar más (ej. separar infra en su propia unidad). La razón: el walking skeleton necesita la infra para ser demostrable; separarla crearía una unidad sin verificación end-to-end posible.
2026-10-16T22:35:00Z — Las dependencias U3→U4 y U3→U5 son de integración, no de compilación. El Order Service puede construirse con mocks de los puertos PaymentPort y EventPublisherPort. Se eligió la dependencia explícita para que la ejecución serial garantice la integración real.

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
