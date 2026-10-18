## Sources

- [desc] Initial description del proyecto Andes Retail.
- [team-practices] Prácticas afirmadas en `team.md`: metodología TDD, orden Red → Green → Refactor, tooling JUnit 5 + Mockito/MockK, floor de cobertura del 80 % de líneas y CI gate.
- [FR-01] Consulta de stock por SKU/bodega/canal.
- [FR-11] Auth OAuth2/OIDC multi-tenant (base).
- [NFR-01] Latencia p95 ≤ 300 ms en consulta de stock.
- [NFR-04] Disponibilidad 99.9 %.
- [NFR-10] Circuit Breaker en adaptadores legacy.
- [NFR-11] Rate limiting por canal.
- [NFR-12] Backpressure: Bulkhead sobre el ERP.
- [CA-01] PCI-DSS base; [CA-02] Backpressure; [CA-03] Clean Architecture.
- [US-01] Consultar stock disponible; [US-08] Auth multi-tenant (base).
- [BR-STK-01..06] Reglas de negocio de `construction/skeleton-stock-query/functional-design/rules.md`.

---

# Instrucciones de prueba — `skeleton-stock-query`

Adaptador de stock (`services/stock-price-adapter`): slice `GET /v1/inventory/stock/{sku}`
autenticado con JWT, con caché Redis de dos horizontes, ACL hacia SAP, Circuit
Breaker y Bulkhead.

## 1. Herramientas y estrategia

| Elemento | Valor |
|---|---|
| Framework | JUnit 5 (`useJUnitPlatform()`) |
| Dobles | MockK 1.13 (servicios y puertos) y Mockito `@MockBean` (rebanadas `@WebMvcTest`) |
| Aserciones | AssertJ 3.25 |
| Arquitectura | ArchUnit 1.5 (`archunit-junit5`) |
| Cliente HTTP simulado | OkHttp MockWebServer |
| Cobertura | JaCoCo, floor del 80 % de líneas (`jacocoTestCoverageVerification`) |
| Estrategia | `standard` (5–8 pruebas por componente, camino feliz + errores + resiliencia + seguridad) |

Los dobles se stubean **con valores exactos** (nunca `any()` en el stub): los
tipos de parámetro Kotlin no admiten `null`, así que un matcher que devuelve
`null` falla en el punto de llamada.

## 2. Comandos (ámbito de esta unidad)

> Todas las rutas están acotadas al módulo `services:stock-price-adapter`.

```bash
# Suite completa + verificación y reporte de cobertura (gate del 80 %)
./gradlew :services:stock-price-adapter:ktlintCheck \
           :services:stock-price-adapter:test \
           :services:stock-price-adapter:jacocoTestCoverageVerification \
           :services:stock-price-adapter:jacocoTestReport

# Sólo dominio y aplicación (bucle TDD)
./gradlew :services:stock-price-adapter:test \
  --tests "com.andesretail.stock.domain.*" \
  --tests "com.andesretail.stock.application.*"

# Sólo reglas de arquitectura limpia (CA-03)
./gradlew :services:stock-price-adapter:test --tests "com.andesretail.stock.arch.*"

# Sólo el slice end-to-end y el arranque del contexto
./gradlew :services:stock-price-adapter:test \
  --tests "com.andesretail.stock.integration.*" \
  --tests "com.andesretail.stock.StockPriceAdapterApplicationTests"
```

En Windows: `gradlew.bat`. El proyecto fija JDK 21 (`jvmToolchain(21)`).

## 3. Catálogo real de pruebas (14 suites, 58 pruebas)

| Suite | Capa | Qué verifica | Pruebas |
|---|---|---|---|
| `domain/model/StockLevelTest` | Dominio | `availableToPromise()`, cantidades no negativas y `reservado ≤ disponible`. | 3 |
| `domain/model/DomainModelTest` | Dominio | Validación de `SKU`/`WarehouseId`, invariantes de `StockReservation` y catálogo de `StockSource`/`ReservationStatus`. | 4 |
| `application/service/GetStockServiceTest` | Aplicación (TDD) | Acierto fresco sin tocar el ERP, miss → ERP + refresco, degradación a obsoleto, *fail-closed* sin obsoleto y tolerancia a una lectura de caché rota. | 6 |
| `infrastructure/cache/RedisStockAdapterTest` | Infraestructura | Claves `stock:fresh:`/`stock:stale:`, TTL 60 s/3600 s, origen `CACHE` vs `STALE_CACHE`, lectura *best-effort* y JSON corrupto. | 8 |
| `infrastructure/client/SapClientTest` | Infraestructura | URL OData, deserialización, propagación del bearer, 500 → `SapClientException`, caída de red → `SapClientException` y corte por timeout de 250 ms. | 5 |
| `infrastructure/client/SapStockResponseTest` | Infraestructura | Deserialización del payload SAP e ignorado de propiedades desconocidas. | 2 |
| `infrastructure/mapper/SapStockMapperTest` | Infraestructura (ACL) | Mapeo al dominio, normalización de negativos, acotado de reservado, `etag` estable y fecha inválida. | 5 |
| `infrastructure/erp/SapStockAdapterTest` | Infraestructura | Traducción al dominio y fallback que convierte cualquier fallo de SAP en `ErpStockUnavailableException`. | 3 |
| `web/controller/StockControllerTest` | Web (`@WebMvcTest`) | Cuerpo de C-01 + cabecera `X-Stock-Source` (`erp`/`stale-cache`), 503 con `Retry-After`, 403 sin scope y 401 sin token, ambos en `problem+json`. | 5 |
| `web/exception/GlobalExceptionHandlerTest` | Web | 400 con `urn:andes:error:bad-request`, 403 sin filtrar el motivo, 503 con `Retry-After` y sin detalle interno, y 500 genérico. | 4 |
| `web/filter/CorrelationIdFilterTest` | Web | Propagación del id entrante, atributo de petición, generación y acotado de longitud, y limpieza del MDC. | 5 |
| `arch/ArchitectureTest` | Arquitectura | `domain` no depende de `application`/`infrastructure`/`web`, y `application` no depende de `infrastructure`. | 2 |
| `integration/StockAdapterIntegrationTest` | Integración | Slice completo servicio + puertos: miss → ERP y degradación a obsoleto. | 2 |
| `StockPriceAdapterApplicationTests` | Contexto (`@SpringBootTest`) | El contexto arranca (los colaboradores son beans reales), las sondas `/actuator/health/liveness` y `/actuator/health/readiness` responden 200 sin credenciales, `/actuator/prometheus` responde 401 sin bearer y los parámetros efectivos de Circuit Breaker/Bulkhead son los del diseño. | 4 |

## 4. Notas de mantenimiento

- **Sin `@SpringBootTest` el fallo se descubre en el despliegue.** Una anotación
  `@Service` ausente o un matcher de Actuator incompleto sólo se detecta si algo
  levanta el contexto completo; de ahí `StockPriceAdapterApplicationTests`.
- **Redis y Keycloak no se levantan en la suite.** El contexto usa un
  `JwtDecoder` local (clave RSA generada en el test) y no abre conexión a Redis:
  el adaptador de caché es perezoso y la degradación es *best-effort*.
- **`@WebMvcTest` importa `SecurityConfig`** y aporta su propio `JwtDecoder`,
  de modo que la cadena de filtros real (incluido `ProblemJsonSecurityHandler`)
  se ejerce sin proveedor de identidad.
- **Resilience4j no está activo en los tests unitarios del adaptador**: sin proxy
  Spring no hay `@CircuitBreaker`. Por eso el *fallback* se invoca por reflexión
  y la degradación se verifica en `GetStockServiceTest`.
- **Los adaptadores con cobertura menor** (`infrastructure.erp`) están cubiertos
  por los tests de la capa que los consume, conforme a la política de `team.md`.

## Assumptions & Open Questions

- [assumption] Los comandos usan `./gradlew` en Linux/macOS y `gradlew.bat` en Windows.
- [assumption] La verificación de cobertura se ejecuta en CI antes del merge; un
  test rojo bloquea el merge sin excepción (práctica afirmada en `team.md`).
- [open-question] El rate limiting de Kong (NFR-11) se verifica con un *load test*
  externo (101 req/s desde un mismo consumer), no dentro de esta suite.
