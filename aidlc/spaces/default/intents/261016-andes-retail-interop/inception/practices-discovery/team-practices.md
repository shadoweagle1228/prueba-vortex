## Way of Working

Usamos **trunk-based development**. Todo el trabajo se mergea a `main` mediante
feature branches de vida corta (máximo 1-2 días). Las ramas de larga duración
acumulan deuda de merge; las evitamos.

Para las ramas de construcción (Construction worktrees), la rama base y el
destino del merge es `main`.

Hacemos **squash-merge** de las ramas de Bolt a `main`. Cada Bolt se convierte
en un commit en el trunk, nombrado por el slug del Bolt.

El equipo de Vortexbird construye todo el proyecto. Las decisiones técnicas son
compartidas con el equipo técnico de AndesRetail.

## Walking Skeleton

Construimos un **walking skeleton**: una versión mínima que recorre todo el
sistema de punta a punta antes de desarrollar los servicios completos.

El skeleton del MVP es: `GET /v1/inventory/stock/{sku}` funcionando end-to-end
desde el canal externo hasta el ERP (via Stock Adapter con Circuit Breaker y
Cache Redis) y de regreso, con autenticación JWT real. Este slice debe estar
verde antes de construir el Order Service.

El criterio de verificación del skeleton es: una llamada autenticada desde un
cliente externo obtiene el stock de un SKU real con latencia p95 < 300ms, y el
Circuit Breaker se activa correctamente cuando el ERP está simulado como offline.

## Testing Posture

- **Methodology**: tdd
- **Methodology evidence**: el equipo escribe el test primero (JUnit 5), luego
  el código mínimo para pasarlo, siguiendo el ciclo Red → Green → Refactor.
- **Ordering**: para cada capa implementable, escribir el test que falla primero,
  luego implementar el código mínimo que lo pasa, luego refactorizar.
- **Tooling**: JUnit 5 + Mockito para servicios Kotlin/Spring Boot. k6 para
  load testing de APIs externas.
- **Coverage floor**: 80% de cobertura de líneas como mínimo antes de merge
  (verificado en CI). Los adaptadores de infraestructura pueden tener cobertura
  menor si los tests de integración los cubren.
- **CI gate**: los tests deben pasar en el pipeline de CI antes de que el PR
  pueda mergearse. Un test rojo bloquea el merge sin excepción.

## Deployment

Deploy automático a **dev** y **staging** en merge a `main`. El deploy a
**producción** requiere aprobación manual del Tech Lead + PO de AndesRetail.

Las herramientas de CD son CodePipeline + CodeBuild (o GitHub Actions — a
confirmar en construction según la preferencia del equipo).

Los entornos (dev, staging, prod) se gestionan como stacks CDK separados con
parámetros de contexto por entorno.

## Code Style

- **Formatter**: ktlint para código Kotlin (formato automático en pre-commit hook
  y en CI).
- **Formatter Python (IaC)**: Black para el código CDK Python.
- **Linter**: ktlint también actúa como linter básico. En construction se evalúa
  agregar detekt para análisis estático más profundo si el equipo lo adopta.
- **Naming conventions**: idiomatic Kotlin (camelCase para funciones y variables,
  PascalCase para clases e interfaces, SCREAMING_SNAKE_CASE para constantes).
  snake_case para Python/CDK.
- **Clean Architecture**: las importaciones deben seguir la Dependency Rule
  (domain no importa application ni infrastructure; application no importa
  infrastructure). Verificado con ArchUnit en tests de arquitectura.
