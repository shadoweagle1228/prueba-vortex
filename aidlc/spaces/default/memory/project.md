# Project-Level Rules

> Project-specific specialisation and corrections. Loaded after `org.md` and
> `team.md` as strict-additive guidance; contradictions with broader policy
> are rejected. Populated by practices-discovery and the self-learning loop.
>
> Use sparingly: most teams don't need a project layer. Reach for it
> only when this specific project needs stable, durable guidance beyond the
> team practice (for example, package-specific release checks or an additional
> regression suite for a legacy component).

## Way of Working

<!-- Project-specific specialisation. Example: -->
<!-- This monorepo requires package-scoped branch names and a package owner -->
<!-- review in addition to the team's normal merge policy. -->

## Walking Skeleton

<!-- Project-specific specialisation. Example: -->
<!-- The walking skeleton must exercise the legacy service adapter as well -->
<!-- as the new service boundary. -->

## Testing Posture

<!-- Project-specific specialisation. -->

## Guard Policy

<!-- Project-specific. Mode: strict, relaxed, or off. Strict here holds for every intent, whatever one person asks in chat; relaxed or off here applies to every intent whose policy came from its scope, unless the person sets another; changing this line changes it. A section under the retired Change Control heading, written by an earlier release, is still read. -->

## Deployment

<!-- Project-specific specialisation. -->

## Code Style

<!-- Project-specific specialisation. -->

## Tech Stack

<!-- Technology choices locked for this project. -->

## Decided

<!-- Decisions made in earlier stages that should not be re-asked. -->
<!-- Format: DECIDED: [decision] (Stage [slug], [date]) -->

## Scope Overrides

<!-- Custom scope rules for this project. -->

## Forbidden

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: NEVER [behavior] (affirmed [date]) -->
<!-- Example: NEVER throw exceptions across service layer boundaries (affirmed 2026-05-17) -->

- NEVER mergear un PR con tests rojos — un test fallido bloquea el merge sin excepción. (affirmed 2026-10-16) (affirmed 2026-10-16)

- NEVER hacer deploy a producción sin aprobación manual explícita del Tech Lead y el PO de AndesRetail. (affirmed 2026-10-16) (affirmed 2026-10-16)

- NEVER escribir código de producción antes de tener un test que falle en los servicios de dominio (aplica TDD). (affirmed 2026-10-16) (affirmed 2026-10-16)

- NEVER crear ramas de larga duración (> 2 días) — las feature branches se integran a main en máximo 1-2 días. (affirmed 2026-10-16) (affirmed 2026-10-16)

## Mandated

<!-- Populated by practices-discovery affirmation gate. -->
<!-- Format: ALWAYS [behavior] (affirmed [date]) -->
<!-- Example: ALWAYS use Result<T,E> for fallible operations in service layer (affirmed 2026-05-17) -->

- ALWAYS escribir el test (JUnit 5) antes del código de producción en los servicios de dominio (TDD cycle: Red → Green → Refactor). (affirmed 2026-10-16) (affirmed 2026-10-16)

- ALWAYS ejecutar ktlint en el pre-commit hook y en CI antes de merge; un formato incorrecto bloquea el merge. (affirmed 2026-10-16) (affirmed 2026-10-16)

- ALWAYS mantener la cobertura de líneas >= 80% en servicios Kotlin antes de merge a main; CI verifica este umbral. (affirmed 2026-10-16) (affirmed 2026-10-16)

- ALWAYS implementar el walking skeleton (slice `GET /v1/inventory/stock/{sku}` end-to-end con CB y cache) antes de construir los servicios de dominio adicionales. (affirmed 2026-10-16) (affirmed 2026-10-16)

- ALWAYS requerir aprobación manual de Tech Lead + PO de AndesRetail para deploys a producción. (affirmed 2026-10-16) (affirmed 2026-10-16)

- ALWAYS verificar la Dependency Rule de Clean Architecture con ArchUnit en los tests de arquitectura de cada servicio. (affirmed 2026-10-16) (affirmed 2026-10-16)

## Corrections

<!-- Project-specific corrections from human feedback. -->
<!-- Format: NEVER/ALWAYS [behavior] (learned [date]) -->
