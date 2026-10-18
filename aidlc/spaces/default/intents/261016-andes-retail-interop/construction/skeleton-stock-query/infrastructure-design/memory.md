<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
2026-10-17T21:38:00Z — El diseño de infraestructura base se definió mediante 5 stacks modulares en AWS CDK Python, garantizando reproducibilidad y parametrización por entorno.
2026-10-17T21:38:00Z — La VPC se configuró en 3 capas de subredes para aislar físicamente el CDE y los componentes de datos de cualquier vector de ataque externo.

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
2026-10-17T21:38:00Z — Se seleccionaron instancias Graviton3 (ARM64) para todos los NodeGroups de EKS y nodos de Redis, ofreciendo una reducción de costo del 20% y un 15% más de rendimiento por vCPU en comparación con x86.
2026-10-17T21:38:00Z — Se fijó la verificación de cobertura de JaCoCo al 80% como puerta dura bloqueante en CI, asegurando que ningún código sin tests suficientes ingrese a main.

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
