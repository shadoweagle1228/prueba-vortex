# Test Results — `skeleton-stock-query`

## Ejecución

- **Comando**: `./gradlew test --no-daemon`
- **Fecha**: 2026-10-18
- **JDK**: Temurin 21
- **Gradle**: 8.7

## Resumen global

| Métrica | Valor |
|---|---|
| **Suites** | 14 |
| **Tests** | 58 |
| **Fallos** | 0 |
| **Errores** | 0 |
| **Omitidos** | 0 |
| **Tiempo** | ~2 min 46 s |

## Detalle por suite

| Suite | Tests | Fallos | Tiempo | Estado |
|---|---|---|---|---|
| `StockLevelTest` | 3 | 0 | 0.3s | ✅ |
| `DomainModelTest` | 4 | 0 | 0.2s | ✅ |
| `GetStockServiceTest` | 6 | 0 | 1.1s | ✅ |
| `RedisStockAdapterTest` | 8 | 0 | 2.3s | ✅ |
| `SapClientTest` | 5 | 0 | 3.2s | ✅ |
| `SapStockResponseTest` | 2 | 0 | 0.1s | ✅ |
| `SapStockMapperTest` | 5 | 0 | 0.4s | ✅ |
| `SapStockAdapterTest` | 3 | 0 | 1.8s | ✅ |
| `StockControllerTest` | 5 | 0 | 4.5s | ✅ |
| `GlobalExceptionHandlerTest` | 4 | 0 | 0.3s | ✅ |
| `CorrelationIdFilterTest` | 5 | 0 | 0.2s | ✅ |
| `ArchitectureTest` | 2 | 0 | 1.2s | ✅ |
| `StockAdapterIntegrationTest` | 2 | 0 | 2.1s | ✅ |
| `StockPriceAdapterApplicationTests` | 4 | 0 | 8.7s | ✅ |

## Casos destacados

### GetStockServiceTest (6 casos TDD)
| Caso | Qué verifica |
|---|---|
| `acierto de cache fresca responde sin invocar al ERP` | `findFresh` hit → 0 llamadas a ERP |
| `sin dato fresco consulta el ERP y refresca la cache` | Miss fresco → ERP → `save` fresco + stale |
| `un valor obsoleto presente no se sirve si el ERP responde` | Stale existe pero ERP sano → sirve ERP |
| `si el ERP cae se sirve el ultimo valor conocido como stale-cache` | ERP falla + stale existe → `source=stale-cache` |
| `fail-closed si el ERP cae y no hay valor obsoleto` | ERP falla + sin stale → 503 `Retry-After: 5` |
| `si la lectura de la cache obsoleta falla se propaga el 503` | ERP falla + stale falla lectura → 503 |

### StockPriceAdapterApplicationTests (4 casos @SpringBootTest)
| Caso | Qué verifica |
|---|---|
| `el contexto de la aplicación carga` | Contexto completo arranca (beans, seguridad, sondas) |
| `las sondas de liveness y readiness son publicas` | `/actuator/health/liveness` y `/readiness` → 200 sin auth |
| `las metricas exigen el scope metrics read` | `/actuator/prometheus` → 401 sin bearer |
| `la resiliencia del ERP toma los parámetros del diseño` | CB/Bulkhead efectivos = ventana 30, wait 60s, bulkhead 20 |

### StockControllerTest (5 casos @WebMvcTest)
| Caso | Qué verifica |
|---|---|
| `responde el cuerpo de C-01 y publica X-Stock-Source` | Body C-01 + cabecera `cache|erp|stale-cache` |
| `marca la contingencia con stale-cache` | Degradación ERP → `X-Stock-Source: stale-cache` |
| `devuelve 503 con Retry-After y problem+json cuando el ERP no responde` | 503 + `Retry-After: 5` + `type=urn:andes:error:stock-unavailable` |
| `rechaza 403 sin el scope del contrato` | Sin `SCOPE_inventory:read` → 403 problem+json |
| `rechaza 401 sin autenticación` | Sin token → 401 problem+json |

### CorrelationIdFilterTest (5 casos)
| Caso | Qué verifica |
|---|---|
| `propaga correlation-id entrante en cabecera respuesta` | Header entrante → header respuesta |
| `genera correlation-id si no viene` | UUID v4 generado y propagado |
| `trunca correlation-id si excede longitud máxima` | > 128 chars → truncado |
| `limpia MDC tras request` | MDC limpio después de `doFilter` |
| `expone correlation-id en atributo de petición` | Atributo `correlationId` disponible para handlers |

## Cobertura (JaCoCo)

| Métrica | Valor | Gate |
|---|---|---|
| **Líneas** | **95,30 %** (345/362) | ≥ 80 % ✅ |
| Ramas | 81,32 % | — |
| Instrucciones | 94,11 % | — |

## Artefactos generados

- `build/test-results/test/*.xml` (JUnit XML)
- `build/reports/jacoco/test/jacocoTestReport.xml`
- `build/reports/tests/test/index.html`