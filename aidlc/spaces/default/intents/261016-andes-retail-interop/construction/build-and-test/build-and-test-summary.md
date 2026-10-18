# Build and Test Summary — `skeleton-stock-query`

## Resumen de ejecución

| Métrica | Valor |
|---|---|
| **Fecha** | 2026-10-18 |
| **Commit** | `HEAD` (working tree, sin commitear) |
| **JDK** | Temurin 21 (`jvmToolchain(21)`) |
| **Gradle** | Wrapper 8.7 |
| **Kotlin** | 1.9.x (Spring Boot 3.2.4) |

## Resultado del build

| Paso | Comando | Estado | Detalle |
|---|---|---|---|
| Estilo (ktlint) | `./gradlew ktlintCheck --no-daemon` | ✅ PASS | 0 violaciones |
| Compilación | `./gradlew compileKotlin --no-daemon` | ✅ PASS | |
| Tests unitarios + integración | `./gradlew test --no-daemon` | ✅ PASS | 58 tests, 0 fallos |
| Cobertura (JaCoCo) | `./gradlew jacocoTestCoverageVerification --no-daemon` | ✅ PASS | **Líneas 95,30 %** (gate ≥ 80 %) |
| Ensamblado JAR | `./gradlew bootJar --no-daemon` | ✅ PASS | `stock-price-adapter-0.1.0.jar` |
| Síntesis CDK | `py -3 infra/app.py` | ✅ PASS | EXIT 0, genera `infra/cdk.out/` |

## Cobertura de código (JaCoCo)

| Métrica | Valor | Gate |
|---|---|---|
| **Líneas** | **95,30 %** (345/362) | ≥ 80 % ✅ |
| Ramas | 81,32 % (74/91) | — |
| Instrucciones | 94,11 % (1933/2054) | — |
| Complejidad | 85,19 % (138/162) | — |
| Métodos | 93,10 % (108/116) | — |
| Clases | 96,15 % (25/26) | — |

### Por paquete (líneas)

| Paquete | Cobertura | Líneas |
|---|---|---|
| `web.controller` | 100,0 % | 8 |
| `web.exception` | 100,0 % | 44 |
| `web.filter` | 100,0 % | 14 |
| `application.service` | 100,0 % | 35 |
| `application.dto` | 100,0 % | 13 |
| `domain.exception` | 100,0 % | 3 |
| `infrastructure.erp` | 100,0 % | 9 |
| `infrastructure.mapper` | 100,0 % | 16 |
| `infrastructure.cache` | 98,3 % | 60 |
| `domain.model` | 98,0 % | 49 |
| `infrastructure.client` | 93,8 % | 48 |
| `web.security` | 83,1 % | 59 |
| `com.andesretail.stock` (main) | 50,0 % | 4 |

> **Nota**: `web.security` 83,1 % por ramas defensivas en `SecurityConfig` y `ProblemJsonSecurityHandler`; `com.andesretail.stock` 50 % es la clase `main` de arranque (no testeable en unidad).

## Suite de pruebas (58 tests, 14 suites)

| Suite | Tests | Capa |
|---|---|---|
| `StockLevelTest` | 3 | Dominio |
| `DomainModelTest` | 4 | Dominio |
| `GetStockServiceTest` | 6 | Aplicación (TDD) |
| `RedisStockAdapterTest` | 8 | Infraestructura (caché) |
| `SapClientTest` | 5 | Infraestructura (cliente HTTP) |
| `SapStockResponseTest` | 2 | Infraestructura (deserialización) |
| `SapStockMapperTest` | 5 | Infraestructura (ACL) |
| `SapStockAdapterTest` | 3 | Infraestructura (ERP + fallback) |
| `StockControllerTest` | 5 | Web (`@WebMvcTest`) |
| `GlobalExceptionHandlerTest` | 4 | Web (problem+json) |
| `CorrelationIdFilterTest` | 5 | Web (filtro) |
| `ArchitectureTest` | 2 | Arquitectura (ArchUnit) |
| `StockAdapterIntegrationTest` | 2 | Integración |
| `StockPriceAdapterApplicationTests` | 4 | Contexto (`@SpringBootTest`) |

## Verificación de calidad

- ✅ **Dependency Rule (CA-03)**: `ArchitectureTest` pasa — dominio sin deps hacia afuera, aplicación sin deps a infraestructura
- ✅ **Contrato C-01**: `StockControllerTest` + `GlobalExceptionHandlerTest` validan cuerpo, cabeceras `X-Stock-Source`, `Retry-After`, `type`/`instance` URN
- ✅ **Seguridad OAuth2**: `SCOPE_inventory:read` exigido, 401/403 en `problem+json`, sondas públicas
- ✅ **Resiliencia**: CircuitBreaker/Bulkhead parámetros efectivos verificados en `@SpringBootTest`
- ✅ **Correlation-id**: Propagación request→response, MDC, problem+json, nunca vacío
- ✅ **Degradación controlada**: Fresco → ERP → Stale → Fail-closed (6 casos en `GetStockServiceTest`)

## Artefactos de entrega

```
services/stock-price-adapter/build/libs/stock-price-adapter-0.1.0.jar
services/stock-price-adapter/build/reports/jacoco/test/jacocoTestReport.xml
services/stock-price-adapter/build/test-results/test/*.xml
infra/cdk.out/*.template.json
deploy/k8s/*.yaml
deploy/kong/*.yaml
deploy/local/docker-compose.yml
deploy/local/keycloak/andes-retail-realm.json
```

## Deuda conocida / no verificado en CI

| Área | Qué falta | Riesgo |
|---|---|---|
| `docker build` | Imagen ARM64 multi-stage | Medio — validado en CI GitHub Actions |
| `docker compose up` | Keycloak 24 + Redis local | Bajo — README documentado |
| `kubectl apply --dry-run` | Manifiestos k8s `deploy/` | Medio — YAML válidos, no dry-run real |
| `cdk deploy` | Despliegue AWS real | Alto — requiere credenciales y cuenta |
| Rate limiting real | Test carga 101 req/s Kong | Medio — NFR-11 validado solo en manifiesto |
| JWKS resolution | Puerto Keycloak 8080 vs 8081 | Bajo — documentado en security-test-instructions |

## Próximos pasos

1. Commit + push a `origin/main` (trigger CI GitHub Actions)
2. Verificar pipeline CI: lint → test → coverage → assemble → docker build (linux/arm64)
3. Ejecutar `cdk deploy` en cuenta AWS destino
4. Validar en staging: sondas, métricas, rate limiting, JWKS resolution