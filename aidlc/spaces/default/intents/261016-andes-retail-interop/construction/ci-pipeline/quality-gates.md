# Quality Gates — `skeleton-stock-query`

## Gates obligatorios (bloqueantes en CI)

| Gate | Comando | Umbral | Fuente | Etapa |
|---|---|---|---|---|
| **Estilo de código** | `./gradlew ktlintCheck` | 0 violaciones | `team.md` (estilo intellij_idea, max_line_length=140) | Code Generation |
| **Tests unitarios + integración** | `./gradlew test` | 0 fallos, 0 errores | `team.md` (TDD, Standard strategy) | Build and Test |
| **Cobertura de líneas (JaCoCo)** | `./gradlew jacocoTestCoverageVerification` | **≥ 80 %** líneas | `team.md` (floor 80 %), `build.gradle.kts` | Build and Test |
| **Ensamblado** | `./gradlew assemble` / `bootJar` | JAR ejecutable generado | Práctica estándar | Build and Test |
| **Imagen contenedor ARM64** | `docker buildx build --platform linux/arm64` | Build exitoso | `infrastructure-specification.md` (Graviton) | CI Pipeline |
| **Síntesis CDK** | `py -3 infra/app.py` | EXIT 0, genera `cdk.out/` | `code-generation-plan.md` | Code Generation |

## Gates de arquitectura (verificados en tests)

| Gate | Verificación | Comando | Bloqueante |
|---|---|---|---|
| **Dependency Rule (CA-03)** | `domain` no depende de `application`/`infrastructure`/`web`; `application` no depende de `infrastructure`/`web` | `./gradlew test --tests "*ArchitectureTest*"` | Sí (ArchUnit) |
| **Contrato C-01** | Body: `sku, available, reserved, lastUpdated, cacheHit`; Header: `X-Stock-Source`; 503: `Retry-After: 5`, `type` URN, `instance` | `./gradlew test --tests "*StockControllerTest*" --tests "*GlobalExceptionHandlerTest*"` | Sí |
| **Seguridad OAuth2** | `SCOPE_inventory:read` exigido; 401/403 en `problem+json`; sondas públicas | `./gradlew test --tests "*StockControllerTest*" --tests "*StockPriceAdapterApplicationTests*"` | Sí |
| **Resiliencia efectiva** | CB: ventana 30, wait 60s, slow-call 250ms/50%; Bulkhead: 20/100ms | `./gradlew test --tests "*StockPriceAdapterApplicationTests.la resiliencia*"` | Sí |
| **Correlation-id** | Propagado request→response, MDC, problem+json nunca vacío | `./gradlew test --tests "*CorrelationIdFilterTest*"` | Sí |

## Gates de rendimiento (no bloqueantes en CI, verificables en staging)

| Métrica | Objetivo | Verificación |
|---|---|---|
| Latencia p95 `GET /v1/inventory/stock/{sku}` | ≤ 300 ms | k6/Gatling en staging |
| Acierto caché fresca | < 15 ms | k6 en staging |
| Timeout ERP efectivo | 250 ms (conexión 100 ms) | `SapClientTest.corta la llamada...` |
| Circuit Breaker estado | CLOSED bajo carga normal | Prometheus `resilience4j_circuitbreaker_state` |
| Bulkhead uso | < 5 disponibles (límite 20) | Prometheus `resilience4j_bulkhead_available_concurrent_calls` |
| Cache hit ratio | > 80 % | Métricas custom / Redis INFO |

## Gates de seguridad (parcialmente en CI)

| Gate | Estado CI | Verificación completa |
|---|---|---|
| **TLS 1.3 + KMS** | No (requiere AWS) | `aws elasticache describe-replication-groups`, `aws rds describe-db-clusters` |
| **Rate limiting Kong (NFR-11)** | No (requiere Kong desplegado) | k6 101 req/s mismo consumer → 429 |
| **JWKS resolution** | Parcial (test usa JwtDecoder local) | `NimbusJwtDecoder.withJwkSetUri` contra Keycloak real |
| **Log sanitization (NFR-08)** | Parcial (logback JSON, sin PII visible) | Grep logs en staging por patrones PII |
| **PCI-DSS CDE isolation** | No (requiere AWS) | Verificar subredes CDE sin ruta a IGW |
| **Dependency Check** | No configurado | `./gradlew dependencyCheckAnalyze` (futuro) |
| **Container Scan** | No configurado | Trivy en imagen ARM64 (futuro) |

## Gates de trazabilidad

| Gate | Verificación |
|---|---|
| **Traceability.json válido** | Sensor `traceability` (estructura OK, destinos existen) |
| **Upstream IDs acotados a unidad** | Revisión manual: solo FR-01, FR-11, NFR-01, NFR-04, NFR-06, NFR-10, NFR-11, NFR-12, CA-01..03, US-01, US-08, BR-STK-01..06 |
| **Cross-unit traceability** | `cross-unit-traceability.md` generado en Build and Test |

## Resumen de gates por etapa

| Etapa | Gates bloqueantes | Gates no bloqueantes (staging) |
|---|---|---|
| **Code Generation** | ktlintCheck, review READY (0 Critical) | — |
| **Build and Test** | test, jacocoTestCoverageVerification (≥80%), assemble, ArchitectureTest | Performance, Security (TLS, Kong, JWKS) |
| **CI Pipeline** | Docker ARM64 build, quality-gates.md documentado | Dependency Check, Trivy, SAST |
| **Deployment Pipeline** | — | `cdk deploy`, validación staging completa |

## Matriz de decisión

| Situación | Acción |
|---|---|
| `ktlintCheck` falla | Ejecutar `ktlintFormat` y recomitar |
| `test` falla | No merge; investigar y fixear test o código |
| Cobertura < 80 % | Añadir tests a componentes descubiertos (ver `build-and-test-summary.md`) |
| `ArchitectureTest` falla | Revisar imports — violación Dependency Rule |
| `StockControllerTest` falla | Revisar contrato C-01 o scope OAuth2 |
| `StockPriceAdapterApplicationTests` falla | Contexto Spring roto — revisar beans, config |
| Docker build falla | Revisar Dockerfile, base image, arquitectura |
| CDK synth falla | Revisar stacks, versiones, dependencias |