## Sources

- [desc] Initial description del proyecto Andes Retail.
- [team-practices] Prácticas afirmadas en team.md y project.md (TDD, Red-Green-Refactor, JaCoCo >= 80%, ArchUnit).
- [tech-stack-decisions.md] JUnit 5, MockK, AssertJ, ArchUnit.
- [functional-spec.md] Casos de prueba de dominio y contratos.

---

# Instrucciones de Pruebas Unitarias — `skeleton-stock-query`

Este documento describe la suite de pruebas automatizadas, el marco de ejecución TDD y los comandos específicos para validar la Unidad 1.

---

## 1. Configuración del Entorno de Pruebas

- **Framework:** JUnit 5 (Jupiter) + MockK 1.13+ + AssertJ 3.25+.
- **Pruebas de Arquitectura:** ArchUnit 1.2+ para verificar la Dependency Rule [CA-03].
- **Métricas de Cobertura:** Plugin JaCoCo configurado con umbral mínimo de cobertura de líneas del 80% [NFR-14].
- **Estrategia Activa:** `Standard strategy` (5 a 8 pruebas por componente, abarcando camino feliz, excepciones de negocio, Circuit Breaker y seguridad OAuth2).

---

## 2. Comandos de Ejecución Específicos para Esta Unidad

> [!IMPORTANT]
> Todos los comandos están delimitados exclusivamente a los paquetes y componentes de la Unidad 1 (`skeleton-stock-query`).

### Ejecución de Pruebas Unitarias de Dominio y Aplicación (TDD)
```bash
./gradlew :services:stock-price-adapter:test --tests "com.andesretail.stock.domain.*" --tests "com.andesretail.stock.application.*"
```

### Ejecución de Pruebas de Arquitectura (ArchUnit)
```bash
./gradlew :services:stock-price-adapter:test --tests "com.andesretail.stock.ArchitectureTest"
```

### Ejecución de la Suite Completa con Reporte de Cobertura JaCoCo
```bash
./gradlew :services:stock-price-adapter:test :services:stock-price-adapter:jacocoTestReport
```

---

## 3. Catálogo de Pruebas a Implementar

| Clase de Prueba | Capa Evaluada | Escenarios Cubiertos | Total Tests |
|---|---|---|---|
| `StockLevelTest` | Dominio | Inmutabilidad de Value Objects, cálculo `hasAvailableStock`, validación de cantidades no negativas | 5 tests |
| `GetStockServiceTest` | Aplicación (TDD) | 1. Cache hit fresco (<60s)<br/>2. Cache miss con ERP exitoso<br/>3. Conmutación a Stale Cache si ERP falla<br/>4. Error 503 si ni ERP ni Stale Cache existen | 6 tests |
| `StockControllerTest` | Infraestructura / REST | 1. Token válido con scope `inventory:read` (200 OK)<br/>2. Sin token Bearer (401 Unauthorized)<br/>3. Token sin scope requerido (403 Forbidden)<br/>4. Propagación de `X-Correlation-ID` en headers | 5 tests |
| `ArchitectureTest` | Gobierno Arquitectónico | Dependencias de paquetes: `domain` no depende de `application` ni `infrastructure` | 3 tests |
| `StockAdapterIntegrationTest`| Integración E2E (Skeleton) | Slice completo desde request HTTP simulado hasta respuesta con Circuit Breaker simulado | 3 tests |

**Total de Pruebas para Unidad 1:** 22 pruebas automatizadas.

---

## 4. Guía de Mocks y Aislamiento

- El puerto `StockCachePort` se simula en pruebas unitarias mediante `mockk<StockCachePort>()`.
- El puerto `ErpStockPort` se simula mediante `mockk<ErpStockPort>()`, permitiendo inyectar excepciones de timeout o fallos de red controlados para verificar las transiciones del Circuit Breaker.
- Las pruebas de controlador utilizan `@WebMvcTest` con `SecurityConfig` para verificar la cadena de filtros sin necesidad de iniciar Redis real.

---

## Assumptions & Open Questions

- [assumption] Los comandos Gradle se ejecutan con `./gradlew` en entornos Linux/macOS y `gradlew.bat` en Windows.
