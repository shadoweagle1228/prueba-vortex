# Resumen de Generación de Código — `skeleton-stock-query`

## Objetivo

Walking skeleton del ecosistema Andes Retail: un slice vertical completo
`GET /v1/inventory/stock/{sku}` — desde un canal externo autenticado con JWT,
pasando por el rate limiting perimetral de Kong, el adaptador de stock (Clean
Architecture, caché Redis de dos horizontes, ACL hacia el ERP con Circuit Breaker
y Bulkhead) y de vuelta — más la infraestructura como código (AWS CDK Python) y
los manifiestos de despliegue que lo sostienen.

## Archivos generados clave

**Servicio (`services/stock-price-adapter`)**

- Raíz del repo: `settings.gradle.kts`, `build.gradle.kts`, `.editorconfig`,
  `README.md`, Gradle Wrapper 8.7, `.github/workflows/ci.yml`.
- Módulo: `services/stock-price-adapter/build.gradle.kts` (Spring Boot 3.2.4,
  toolchain JDK 21, OAuth2 JWT, Resilience4j, Redis, JaCoCo ≥ 80 %, ArchUnit,
  MockK, Mockito, JUnit 5, logstash-logback-encoder).
- Dominio (sin dependencias hacia fuera): `domain/model/*` (`SKU`, `WarehouseId`,
  `StockSource`, `ReservationStatus`, `StockLevel`, `StockReservation`),
  `domain/port/*` (`StockCachePort`, `ErpStockPort`) y
  `domain/exception/ErpStockUnavailableException`.
- Aplicación: `application/dto/*` (`StockQueryCommand`, `StockResponseDto`,
  `StockQueryResult`), `application/port/GetStockUseCase`,
  `application/service/GetStockService`.
- Infraestructura: `infrastructure/cache/RedisStockAdapter`,
  `infrastructure/erp/SapStockAdapter`, `infrastructure/client/SapClient` +
  `SapStockResponse` + `SapClientException`, `infrastructure/mapper/SapStockMapper`.
- Web: `web/controller/StockController`, `web/exception/GlobalExceptionHandler`,
  `web/security/SecurityConfig` + `ProblemJsonSecurityHandler`,
  `web/filter/CorrelationIdFilter`.
- Config: `application.yml`, `logback-spring.xml` (JSON estructurado).
- Contenedor: `services/stock-price-adapter/Dockerfile` multi-stage (buildx,
  runtime JRE Alpine ARM64, `HEALTHCHECK` sobre `/actuator/health/liveness`).

## Infraestructura y entrega

- **IaC**: `infra/app.py`, `infra/cdk.json`, `infra/requirements.txt`,
  `infra/network_stack.py`, `infra/security_stack.py`, `infra/data_stack.py`,
  `infra/compute_stack.py`, `infra/README.md`.
- **Despliegue**: `deploy/k8s/*` (namespace, serviceaccount, configmap,
  deployment, service, hpa, networkpolicy + PDB, kustomization),
  `deploy/k8s/monitoring/` (ServiceMonitor con `bearerTokenSecret`),
  `deploy/kong/` (rate limiting por canal y consumers) y `deploy/local/`
  (Keycloak 24 + Redis para ejecutar el slice de punta a punta en local).
- **CI**: `.github/workflows/ci.yml` (lint → test → gate de cobertura → assemble →
  imagen `linux/arm64`).

## Pruebas (14 suites, 58 casos)

| Suite | Capa | Casos |
|---|---|---|
| `domain/model/StockLevelTest` | Dominio | 3 |
| `domain/model/DomainModelTest` | Dominio | 4 |
| `application/service/GetStockServiceTest` | Aplicación (TDD) | 6 |
| `infrastructure/cache/RedisStockAdapterTest` | Infraestructura | 8 |
| `infrastructure/client/SapClientTest` | Infraestructura | 5 |
| `infrastructure/client/SapStockResponseTest` | Infraestructura | 2 |
| `infrastructure/mapper/SapStockMapperTest` | Infraestructura (ACL) | 5 |
| `infrastructure/erp/SapStockAdapterTest` | Infraestructura | 3 |
| `web/controller/StockControllerTest` | Web (`@WebMvcTest`) | 5 |
| `web/exception/GlobalExceptionHandlerTest` | Web | 4 |
| `web/filter/CorrelationIdFilterTest` | Web | 5 |
| `arch/ArchitectureTest` | Arquitectura | 2 |
| `integration/StockAdapterIntegrationTest` | Integración | 2 |
| `StockPriceAdapterApplicationTests` | Contexto (`@SpringBootTest`) | 4 |

## Verificación ejecutada

```bash
export JAVA_HOME=<jdk-21>
./gradlew ktlintCheck test jacocoTestCoverageVerification jacocoTestReport --no-daemon
```

| Verificación | Resultado |
|---|---|
| `ktlintCheck` | Sin violaciones (estilo `intellij_idea`, `max_line_length = 140`) |
| `test` | 58 casos, 0 fallos / 0 errores |
| `jacocoTestCoverageVerification` | OK — gate de líneas >= 80 % |
| `python infra/app.py` | EXIT 0 — sintetiza `infra/cdk.out/` sin credenciales AWS |

## Decisiones de implementación durante la generación

1. **Caché dual de Redis**: claves `stock:fresh:{sku}:{bodega}` (TTL 60 s) y
   `stock:stale:{sku}:{bodega}` (TTL 3600 s) para sostener la degradación
   controlada (BR-STK-01/03). La caché es *best-effort*: un Redis caído degrada a
   consulta directa al ERP y nunca rompe la petición.
- **Resiliencia del adaptador ERP**: `@CircuitBreaker` + `@Bulkhead` sobre
  `SapClient`; el fallback traduce el fallo a `ErpStockUnavailableException` (503
  `application/problem+json` con `Retry-After`) sin filtrar el detalle interno
  del ERP (BR-STK-02/04/05, NFR-10/NFR-12).
- **ACL de normalización**: `SapStockMapper` normaliza cantidades negativas de SAP
  y acota el reservado al disponible (BR-STK-06).
- **Caché partida en el puerto**: `StockCachePort` expone `findFresh`/`findStale`
  en lugar de un único `get`, para que la degradación sea una decisión explícita
  del caso de uso y no un efecto colateral del TTL.
- **El origen del dato viaja en cabecera**: `X-Stock-Source: cache|erp|stale-cache`,
  nunca en el cuerpo, que queda estrictamente ceñido al contrato C-01.
- **403 explícito**: `GlobalExceptionHandler` maneja `AccessDeniedException`; sin
  él, la familia `Exception` devolvía 500 ante las denegaciones de `@PreAuthorize`.
- **`ProblemJsonSecurityHandler` como bean de `SecurityConfig`** (no
  `@Component`): permite que `@WebMvcTest` + `@Import(SecurityConfig::class)` no
  duplique beans y ejerza la cadena de seguridad real.
- **Sondas y métricas**: `/actuator/health/**` público para las sondas, grupos
  `liveness`/`readiness` declarados explícitamente para que un Redis caído no
  expulse al pod; `/actuator/prometheus` y `/actuator/metrics/**` detrás de
  `SCOPE_metrics:read` (fail-closed) con `ServiceMonitor` que aporta el bearer y
  `NetworkPolicy` que abre el ingress sólo a `kong` y `monitoring`.
- **Keycloak sobre la Aurora del stack de datos** en lugar de un Postgres efímero:
  entrega FR-11/US-08 con un emisor OIDC real y persistente, no un mock.

## Decisiones tomadas y desviaciones respecto al plan

1. **`GetStockUseCase` devuelve `StockQueryResult`** (valor + `StockSource`) y no
   `StockLevel`: hace falta propagar el origen (`cache`/`erp`/`stale-cache`) hasta
   el adaptador web para emitir `X-Stock-Source`.
2. **Los puertos de caché cambiaron de firma** respecto al `functional-spec`
   original (`get`/`getStale` → `findFresh`/`findStale`): el nombre expresa que
   hay dos horizontes de vigencia y no un único almacén.
3. **`ErpStockUnavailableException` vive en `domain/exception`**, no en
   `infrastructure`: el caso de uso la lanza como regla de negocio y la capa
   externa no puede ser conocida por el dominio (CA-03, Dependency Rule).
4. **Pasos `Frontend behavior` del `plan_profile` no aplicables**: esta unidad es
   un servicio backend sin interfaz propia; quedan registrados como no ejecutados
   en `code-generation-plan.md`.
5. **`aws-cdk-lib` fijado a 2.200.0** (el `requirements.txt` original declaraba
   2.135.0) y añadido `aws-cdk.lambda-layer-kubectl-v29`: en 2.200 `eks.Cluster`
   exige `kubectl_layer` y `add_helm_chart` lo necesita.
6. **Kong, Keycloak 24 y External Secrets se aprovisionan vía Helm en CDK**; el
   rate limiting se declara en manifiestos de `deploy/kong/` porque Kong no
   publica los CRDs desde CDK.
7. **Se descartó el puerto de gestión separado para Actuator**: no es verificable
   en este entorno y el aislamiento se obtiene con `NetworkPolicy` + bearer.

## Verificación y honestidad de la evidencia

- Comandos ejecutados: `./gradlew ktlintCheck test jacocoTestCoverageVerification
  jacocoTestReport --console=plain --no-daemon` (JDK 21, Gradle Wrapper 8.7) y
  `py -3 infra/app.py` (síntesis CDK sin credenciales AWS).
- Resultado del build verificado localmente: `BUILD SUCCESSFUL`, **58 pruebas en
  14 suites, 0 fallos / 0 errores / 0 omitidas**, sin violaciones de ktlint.

| Gate | Resultado |
|---|---|
| `ktlintCheck` | OK (estilo `intellij_idea`, `max_line_length = 140`) |
| `test` | 58/58 verdes |
| `jacocoTestCoverageVerification` | OK — líneas **95,30 %** (345/362), gate `>= 80 %` |

Cobertura de líneas por paquete (de mayor a menor):

| Paquete | Líneas | Ramas |
|---|---|---|
| `web.controller` | 100,0 % | — |
| `web.exception` | 100,0 % | 75,0 % |
| `web.filter` | 100,0 % | 90,0 % |
| `application.service` | 100,0 % | 100,0 % |
| `application.dto` | 100,0 % | — |
| `domain.exception` | 100,0 % | — |
| `infrastructure.erp` | 100,0 % | — |
| `infrastructure.mapper` | 100,0 % | 100,0 % |
| `infrastructure.cache` | 98,3 % | 100,0 % |
| `domain.model` | 98,0 % | 81,6 % |
| `infrastructure.client` | 93,8 % | 75,0 % |
| `web.security` | 83,1 % | 50,0 % |
| `com.andesretail.stock` (`main` de arranque) | 50,0 % | — |

- **No verificado en este entorno**: `docker build` (la imagen ARM64 se construye
  en CI), `docker compose -f deploy/local/docker-compose.yml up` (requiere Docker
  en el host), `kubectl apply --dry-run` sobre los manifiestos de `deploy/` y el
  `cdk deploy` real contra AWS. Los YAML/JSON sí se validaron como documentos bien
  formados y el ensamblado CDK sintetiza completo.
- La cobertura de líneas se mide con JaCoCo sobre `main`; el gate del 80 % es el
  de `team.md` (el ID `NFR-14` pertenece a otra unidad y no se usa aquí).
- Las líneas no cubiertas son el `main` de arranque (no ejecutable en un test) y
  las ramas defensivas de `web.security` y `infrastructure.client`.

## Deuda / fuera del alcance de esta unidad

- `FR-06` (reserva/liberación de stock): `StockReservation` existe como modelo de
  dominio, pero el flujo de reserva no se implementa → `Deferred` en trazabilidad.
- `FR-11` se entrega como **base** (un realm, tres clientes, scopes por canal); el
  multi-tenant completo y la federación por marca quedan para otra unidad.
- El rate limiting de NFR-11 se declara en Kong; su verificación requiere un
  *load test* externo (101 req/s desde un mismo consumer).
- La observabilidad completa (dashboards, alertas, trazas) pertenece a la fase de
  operación; aquí sólo se entrega el endpoint de métricas protegido.

## Estado de la iteración de revisión

- Iteración 1: `NOT-READY` (5 Critical, 6 Major, 2 Minor) → corregidos en esta
  revisión: estereotipo Spring + `@SpringBootTest`, scope `SCOPE_inventory:read`,
  cuerpo C-01 y `X-Stock-Source`, `problem+json` con `urn:andes:error:*`,
  degradación `stale-cache` + *fail-closed*, parámetros de resiliencia con prueba
  de los valores efectivos, artefactos de Kong/Keycloak/monitoreo/NetworkPolicy,
  trazabilidad acotada a `units.md` y documentación de pruebas alineada al
  catálogo real. Hallazgos abiertos (si los hay) se listan en el informe de la
  iteración 2.
