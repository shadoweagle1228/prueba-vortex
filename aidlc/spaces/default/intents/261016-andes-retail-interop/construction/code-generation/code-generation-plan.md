## Summary

- Builds: Microservicio `stock-price-adapter` (Kotlin + Spring Boot 3.2 con Clean Architecture), stacks IaC en AWS CDK Python (VPC, KMS, Aurora PostgreSQL, Redis, EKS + Kong + Keycloak) y manifiestos de despliegue para el Walking Skeleton.
- Touches: `settings.gradle.kts`, `build.gradle.kts`, `services/stock-price-adapter/`, `infra/`, `deploy/`, `Dockerfile`.
- Tests: 58 pruebas automatizadas en 14 suites (unitarias TDD, adaptadores de caché y ERP, controlador OAuth2, handler `problem+json`, filtro de correlación, ArchUnit de arquitectura limpia, integración del skeleton y arranque del contexto completo con sondas de Kubernetes y parámetros efectivos de resiliencia).

---

## Testing Contract

```json
{
  "version": 1,
  "methodology": "tdd",
  "source": "team",
  "ordering": "para cada capa implementable, escribir el test que falla primero, luego implementar el código mínimo que lo pasa, luego refactorizar.",
  "scope": "feature",
  "test_strategy": "standard",
  "project_type": "greenfield",
  "applicable_notes": [
    {
      "layer": "org",
      "text": "We treat tests as a first-class deliverable in every Bolt. The specific\nmethodology (TDD, BDD, ATDD, or classic test-after) is affirmed at\npractices-discovery and recorded in `team.md` under this heading with explicit\n`Methodology` and `Ordering` fields; Code Generation resolves those fields\nindependently from coverage, tooling, and scope notes.\n\nWhen no posture has been affirmed, our default per scope is:\n- **Methodology**: test-after\n- **Ordering**: implement each applicable testable layer, then write and run\n  that layer's tests.\n- `mvp`, `enterprise`, `feature`, `infra`, `classic` add an 80% line-coverage\n  floor and CI execution before merge.\n- `bugfix`, `security-patch` add a targeted regression for the specific\n  bug/vulnerability and require the existing suite to remain green.\n- `express` uses the Minimal strategy: requirement-driven unit tests (one per\n  requirement, with a happy-path floor per component); existing tests remain\n  green.\n- `poc`, `refactor`, `workshop` add no extra new-test floor and require the\n  existing suite to remain green.\n\nThe active `Test Strategy` still applies in every scope and determines test\nvolume/types. Scope floors are additive; they never reduce or replace the\nselected strategy.\n\nBuild and Test verifies defined coverage floors and affirmed quality targets;\nthey may not be weakened to make a step pass.\n\nAffirm a stricter posture in `team.md` if the team commits to one."
    },
    {
      "layer": "team",
      "text": "- **Methodology**: tdd\n- **Methodology evidence**: el equipo escribe el test primero (JUnit 5), luego\n  el código mínimo para pasarlo, siguiendo el ciclo Red → Green → Refactor.\n- **Ordering**: para cada capa implementable, escribir el test que falla primero,\n  luego implementar el código mínimo que lo pasa, luego refactorizar.\n- **Tooling**: JUnit 5 + Mockito para servicios Kotlin/Spring Boot. k6 para\n  load testing de APIs externas.\n- **Coverage floor**: 80% de cobertura de líneas como mínimo antes de merge\n  (verificado en CI). Los adaptadores de infraestructura pueden tener cobertura\n  menor si los tests de integración los cubren.\n- **CI gate**: los tests deben pasar en el pipeline de CI antes de que el PR\n  pueda mergearse. Un test rojo bloquea el merge sin excepción."
    }
  ],
  "obligations": {
    "strategy": "standard",
    "strategy_volume": [
      "Five to eight tests per component.",
      "Unit tests plus integration tests for key boundaries.",
      "Add E2E, performance, or security tests when requirements demand them."
    ],
    "scope_floor": [
      "Meet an 80% line-coverage floor.",
      "Run the selected tests in CI before merge."
    ],
    "combination_rule": "Apply every selected-strategy obligation and every scope-floor obligation; neither replaces the other, and a targeted scope regression may add the narrowest necessary test type beyond the strategy default."
  },
  "plan_profile": {
    "methodology": "tdd",
    "runner_step": "Bootstrap the minimal test runner/configuration and record the exact unit-scoped command.",
    "runner_ready_before_first_test": true,
    "testable_layers": [
      "Data model / database behavior",
      "Repository / data access",
      "Business logic",
      "API / endpoint",
      "Frontend behavior"
    ],
    "steps": [
      "Project structure and production configuration skeleton.",
      "Bootstrap the minimal test runner/configuration and record the exact unit-scoped command.",
      "Data model / database behavior - Red: write the failing tests and record the failing command output.",
      "Data model / database behavior - Green: implement only enough behavior to pass.",
      "Data model / database behavior - Refactor: improve the implementation while tests stay green.",
      "Repository / data access - Red: write the failing tests and record the failing command output.",
      "Repository / data access - Green: implement only enough behavior to pass.",
      "Repository / data access - Refactor: improve the implementation while tests stay green.",
      "Business logic - Red: write the failing tests and record the failing command output.",
      "Business logic - Green: implement only enough behavior to pass.",
      "Business logic - Refactor: improve the implementation while tests stay green.",
      "API / endpoint - Red: write the failing tests and record the failing command output.",
      "API / endpoint - Green: implement only enough behavior to pass.",
      "API / endpoint - Refactor: improve the implementation while tests stay green.",
      "Frontend behavior - Red: write the failing tests and record the failing command output.",
      "Frontend behavior - Green: implement only enough behavior to pass.",
      "Frontend behavior - Refactor: improve the implementation while tests stay green.",
      "Environment/build configuration.",
      "Documentation and traceability."
    ]
  },
  "input_sha256": "sha256:7c912e6db19f8f19812fae2a2250522e09e7c85d5fa5a5be8188562f5846e640",
  "contract_sha256": "sha256:32dd3df86659bac29ccf828e0e29f41fa7affcd217ba9592a37fc35d0738a09f"
}
```

---

## Sources

- [desc] Initial description del proyecto Andes Retail.
- [team-practices] Prácticas afirmadas en team.md y project.md (TDD Red-Green-Refactor, JaCoCo >= 80%, ArchUnit).
- [functional-spec.md] Modelo de dominio, puertos y máquina de estados.
- [nfr-design.md] Circuit Breaker, Redis dual cache, Spring Security.
- [infrastructure-specification.md] Recursos AWS CDK Python y configuración EKS.
- [unit-test-instructions.md] Suite de pruebas y comandos de verificación.

---

# Plan de Generación de Código — `skeleton-stock-query` (Walking Skeleton)

Este plan detalla los pasos de implementación ordenados bajo metodología **TDD** (*Test-Driven Development: Red → Green → Refactor*) conforme a las prácticas afirmadas del equipo.

---

## Trazabilidad de Historias a Pasos de Código

| Paso de Código | Componente Implementado | Historias de Usuario Satisfechas | Requerimientos Clave |
|---|---|---|---|
| **Paso 1** | Scaffolding de Proyecto y Gradle Wrapper | Habilitador de Plataforma | `TF-stack`, `memory:M1` (floor 80%) |
| **Paso 2** | Stacks IaC AWS CDK Python (red, KMS, datos, cómputo) | Habilitador de Infraestructura | NFR-04, NFR-06, CA-01 |
| **Paso 3** | Capa de Dominio (Modelos & Value Objects) | US-01 (Consultar Stock) | FR-01, CA-03 |
| **Paso 3b** | Puertos de Dominio & Casos de Uso (TDD Red → Green) | US-01 | FR-01, CA-02, CA-03, BR-STK-01, BR-STK-03, BR-STK-04 |
| **Paso 4** | Adaptadores de Infraestructura (Redis fresca/obsoleta & SAP ERP con Resilience4j) | US-01 | FR-01, NFR-01, NFR-10, NFR-12, CA-02, BR-STK-02, BR-STK-05, BR-STK-06 |
| **Paso 5** | Adaptador Web REST, `problem+json` y Seguridad OAuth2 JWT | US-01, US-08 | FR-01, FR-11, CA-01, BR-STK-04 |
| **Paso 6** | Rate limiting perimetral en Kong y proveedor OIDC Keycloak | US-08 | FR-11, NFR-04, NFR-11, CA-02 |
| **Paso 7** | Pruebas de Arquitectura (ArchUnit), sondas y Dockerfile | Habilitador de Calidad | CA-03, NFR-04 |

---

## Lista de Pasos de Implementación (Checklist de Ejecución)

> Los pasos `Frontend behavior` del `plan_profile` del contrato **no aplican** a
> esta unidad: `skeleton-stock-query` es un servicio backend sin capa de
> presentación propia, así que no se generó ni se probó código de frontend. La
> lista siguiente registra el estado real de los pasos aplicables.

### Fase 1: Scaffolding y Entorno de Construcción
- [x] **Paso 1.1:** Crear estructura raíz del proyecto multi-módulo con `settings.gradle.kts` y `build.gradle.kts`.
- [x] **Paso 1.2:** Configurar dependencias clave (Spring Boot 3.2.4, Kotlin 1.9, Resilience4j, Lettuce, JUnit 5, MockK, ArchUnit, JaCoCo con floor al 80%).
- [x] **Paso 1.3:** Crear `build.gradle.kts` del módulo `services/stock-price-adapter` y `.editorconfig` (max_line_length 140).

### Fase 2: Infraestructura como Código (AWS CDK en Python)
- [x] **Paso 2.1:** Crear `infra/app.py` y `infra/cdk.json` con dependencias en `infra/requirements.txt`.
- [x] **Paso 2.2:** Implementar `infra/network_stack.py` (VPC de 3 niveles con subredes CDE aisladas).
- [x] **Paso 2.3:** Implementar `infra/security_stack.py` (CMK con rotación anual y secreto de SAP).
- [x] **Paso 2.4:** Implementar `infra/data_stack.py` (ElastiCache Redis 7.1 Multi-AZ `cache.t4g.medium` con TLS y cifrado con la CMK, y Aurora **PostgreSQL 16** Serverless v2 en subredes aisladas).
- [x] **Paso 2.5:** Implementar `infra/compute_stack.py` (EKS 1.29 con NodeGroup Graviton3 `c7g.medium` de 2 a 6 nodos, addon `metrics-server`, Kong Ingress Controller, Keycloak 24 sobre la Aurora y External Secrets Operator).
- [x] **Paso 2.6:** Verificar el ensamblado con `python infra/app.py` (sintetiza `infra/cdk.out/` sin credenciales AWS).

### Fase 3: Capa de Dominio Pura (Clean Architecture)
- [x] **Paso 3.1:** Crear Value Objects `SKU.kt`, `WarehouseId.kt`, `StockSource.kt`, `ReservationStatus.kt`.
- [x] **Paso 3.2:** Implementar agregado raíz `StockLevel.kt` y entidad `StockReservation.kt`.
- [x] **Paso 3.3 (TDD Red → Green):** Crear las suites `StockLevelTest.kt` y `DomainModelTest.kt` validando inmutabilidad, cantidades no negativas y catálogo de orígenes.
- [x] **Paso 3.4:** Definir puertos de dominio `StockCachePort.kt` (`findFresh`/`findStale`/`save`), `ErpStockPort.kt` y la excepción de dominio `ErpStockUnavailableException.kt`.

### Fase 4: Capa de Aplicación (Casos de Uso)
- [x] **Paso 4.1:** Definir DTOs y comandos `StockQueryCommand.kt`, `StockResponseDto.kt` y `StockQueryResult.kt`.
- [x] **Paso 4.2:** Definir interfaz `GetStockUseCase.kt`.
- [x] **Paso 4.3 (TDD Red):** Escribir `GetStockServiceTest.kt` con acierto fresco, consulta al ERP, refresco de caché, degradación a obsoleto y fail-closed.
- [x] **Paso 4.4 (TDD Green):** Implementar `GetStockService.kt` (`@Service`) con el orden fresco → ERP → obsoleto.

### Fase 5: Capa de Infraestructura y Adaptadores
- [x] **Paso 5.1:** Implementar `RedisStockAdapter.kt` con las claves `stock:fresh:`/`stock:stale:` y lectura tolerante a fallos de Redis.
- [x] **Paso 5.2:** Implementar `SapStockAdapter.kt` con Resilience4j (`@CircuitBreaker`, `@Bulkhead`) y fallback que traduce a `ErpStockUnavailableException`.
- [x] **Paso 5.3:** Implementar el mapper ACL `SapStockMapper.kt` y el cliente HTTP `SapClient.kt` (timeouts 100/250 ms, pool de 20 conexiones).
- [x] **Paso 5.4:** Implementar el controlador `StockController.kt` con el endpoint `GET /v1/inventory/stock/{sku}`, la cabecera `X-Stock-Source` y el scope `inventory:read`.
- [x] **Paso 5.5:** Implementar `GlobalExceptionHandler.kt` con `application/problem+json`, tipos `urn:andes:error:*` y `Retry-After: 5` en el 503.
- [x] **Paso 5.6:** Implementar `SecurityConfig.kt` (JWT, `SCOPE_*`, sondas públicas, métricas protegidas), `ProblemJsonSecurityHandler.kt` (401/403 RFC 7807) y `CorrelationIdFilter.kt`.
- [x] **Paso 5.7:** Crear `application.yml` (resiliencia, Redis, SAP, management) y `logback-spring.xml` (JSON a consola).

### Fase 6: Pruebas de Integración, Calidad y Despliegue
- [x] **Paso 6.1:** Implementar `StockControllerTest.kt` y `GlobalExceptionHandlerTest.kt` contra el contrato C-01.
- [x] **Paso 6.2:** Implementar `ArchitectureTest.kt` (ArchUnit) y `CorrelationIdFilterTest.kt`.
- [x] **Paso 6.3:** Implementar `StockAdapterIntegrationTest.kt` y `StockPriceAdapterApplicationTests.kt` (arranque del contexto completo y sondas de Kubernetes).
- [x] **Paso 6.4:** Crear `Dockerfile` multi-stage para JDK 21 optimizado para ARM64 Graviton y `.dockerignore`.
- [x] **Paso 6.5:** Crear los manifiestos `deploy/k8s/` (deployment con sondas y `securityContext`, service, configmap, HPA, NetworkPolicy con ingress de `kong`/`monitoring` y PDB).
- [x] **Paso 6.6:** Crear `deploy/kong/` (rate limiting por canal, NFR-11), `deploy/k8s/monitoring/` (ServiceMonitor y alertas) y `deploy/local/` (Keycloak 24 + Redis para el slice end-to-end local).

---

## Assumptions & Open Questions

- [assumption] Todos los pasos se ejecutan de manera estricta y progresiva, marcando las casillas correspondientes a medida que se generan los archivos en el espacio de trabajo.
- [desviación] `Frontend behavior` no tiene código asociado en esta unidad: el walking skeleton no expone interfaz de usuario. Los pasos de esa capa del `plan_profile` quedan deliberadamente sin ejecutar.
- [desviación] El checklist se ejecutó en el orden *componente → prueba del componente* dentro de cada fase, no con todas las pruebas rojas primero a nivel global: `GetStockServiceTest` se escribió antes de `GetStockService` (Paso 4.3 → 4.4), `RedisStockAdapterTest`/`SapStockAdapterTest` antes de sus adaptadores (Paso 5) y `StockControllerTest`/`GlobalExceptionHandlerTest`/`CorrelationIdFilterTest` antes de la capa web (Paso 6). El ciclo Red → Green → Refactor se respetó por componente, que es el modo en que `team.md` afirma TDD.
