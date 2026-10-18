# Instrucciones de Pruebas de Integración — `skeleton-stock-query`

## Alcance

Pruebas de límites clave entre componentes del walking skeleton:
- Adaptador de stock ↔ Redis (caché fresca/stale)
- Adaptador de stock ↔ SAP ERP (vía ACL + Resilience4j)
- Cadena de seguridad OAuth2/JWT end-to-end
- Sondas de Kubernetes (liveness/readiness) y métricas protegidas

## Entorno de pruebas

Las pruebas de integración **no** levantan Redis, Keycloak ni SAP reales. Usan:
- `@SpringBootTest` con `@AutoConfigureMockMvc` (`StockPriceAdapterApplicationTests`)
- `MockWebServer` para simular SAP (`SapClientTest`)
- `MockK`/`Mockito` para dobles de puertos en tests de servicio (`GetStockServiceTest`)
- `JwtDecoder` local generado en test (evita resolución JWKS remota)

## Ejecución

```bash
# Suite completa de integración (contexto Spring completo)
./gradlew :services:stock-price-adapter:test \
  --tests "com.andesretail.stock.StockPriceAdapterApplicationTests" \
  --tests "com.andesretail.stock.integration.*" \
  --no-daemon

# Solo pruebas de adaptadores con MockWebServer
./gradlew :services:stock-price-adapter:test \
  --tests "com.andesretail.stock.infrastructure.client.*" \
  --no-daemon
```

## Casos de integración cubiertos

| Test | Qué verifica |
|---|---|
| `StockPriceAdapterApplicationTests.el contexto de la aplicación carga` | Contexto Spring completo arranca (beans, seguridad, sondas) |
| `StockPriceAdapterApplicationTests.las sondas de liveness y readiness son publicas` | `/actuator/health/liveness` y `/readiness` → 200 sin auth |
| `StockPriceAdapterApplicationTests.las metricas exigen el scope metrics read` | `/actuator/prometheus` → 401 sin bearer |
| `StockPriceAdapterApplicationTests.la resiliencia del ERP toma los parámetros del diseño` | CircuitBreaker/Bulkhead efectivos = diseño (ventana 30, wait 60s, bulkhead 20) |
| `StockAdapterIntegrationTest.skeleton end-to-end cache miss then ERP` | Miss fresco → consulta ERP → refresca caché → respuesta `source=erp` |
| `StockAdapterIntegrationTest.skeleton end-to-end ERP caido sirve contingencia obsoleta` | ERP falla → sirve stale-cache → cabecera `X-Stock-Source: stale-cache` |
| `SapClientTest.deserializa el payload de SAP y arma la URL esperada` | Cliente HTTP forma URL OData correcta y deserializa |
| `SapClientTest.corta la llamada cuando SAP excede el presupuesto de tiempo` | Timeout 250 ms efectivo (corta respuesta tardía) |

## Verificación de calidad

- **Cobertura de líneas objetivo**: ≥ 80 % (gate en `jacocoTestCoverageVerification`)
- **Estrategia**: Standard (5-8 tests/componente + integración en límites)
- **Orden**: Contract-first (tests de controlador contra C-01) → Component → Integración

## No cubierto en esta unidad (deuda conocida)

- Test de carga real contra Kong (NFR-11: 101 req/s mismo consumer) → requiere entorno desplegado
- Test de resolución JWKS contra Keycloak real (puerto 8080 vs 8081 en `application.yml`)
- Despliegue real k8s + `kubectl apply --dry-run` de manifiestos `deploy/`
- `cdk deploy` contra AWS real