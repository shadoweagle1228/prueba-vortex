## Summary

- Builds: Microservicio `stock-price-adapter` (Kotlin + Spring Boot 3.2 con Clean Architecture) y stacks IaC en AWS CDK Python para el Walking Skeleton.
- Touches: `settings.gradle.kts`, `build.gradle.kts`, `services/stock-price-adapter/`, `infra/`, `Dockerfile`.
- Tests: 22 pruebas automatizadas (unitarias TDD, controlador OAuth2, ArchUnit de arquitectura limpia e integración del skeleton).

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
| **Paso 1** | Scaffolding de Proyecto y Gradle Wrapper | Habilitador de Plataforma | TECH-STACK-01 |
| **Paso 2** | Stacks IaC AWS CDK Python | Habilitador de Infraestructura | INFRA-NET-01, INFRA-DATA-01 |
| **Paso 3** | Capa de Dominio (Modelos & Value Objects) | US-01 (Consultar Stock) | FR-01, CA-03 |
| **Paso 4** | Pruebas de Dominio (TDD Red) | US-01 | NFR-14 |
| **Paso 5** | Puertos de Dominio & Casos de Uso (TDD Red → Green) | US-01, US-08 | FR-01, CA-02, CA-03 |
| **Paso 6** | Adaptadores de Infraestructura (Redis & SAP ERP con Resilience4j) | US-01 | FR-01, NFR-01, NFR-10, NFR-12 |
| **Paso 7** | Adaptador Web REST & Seguridad OAuth2 JWT | US-01, US-08 | FR-01, FR-11, NFR-06, NFR-07 |
| **Paso 8** | Pruebas de Arquitectura (ArchUnit) y Dockerfile | Habilitador de Calidad | CA-03, NFR-05 |

---

## Lista de Pasos de Implementación (Checklist de Ejecución)

### Fase 1: Scaffolding y Entorno de Construcción
- [ ] **Paso 1.1:** Crear estructura raíz del proyecto multi-módulo con `settings.gradle.kts` y `build.gradle.kts`.
- [ ] **Paso 1.2:** Configurar dependencias clave (Spring Boot 3.2.4, Kotlin 1.9, Resilience4j, Lettuce, JUnit 5, MockK, ArchUnit, JaCoCo con floor al 80%).
- [ ] **Paso 1.3:** Crear `build.gradle.kts` del módulo `services/stock-price-adapter`.

### Fase 2: Infraestructura como Código (AWS CDK en Python)
- [ ] **Paso 2.1:** Crear `infra/app.py` y `infra/cdk.json` con dependencias en `infra/requirements.txt`.
- [ ] **Paso 2.2:** Implementar `infra/network_stack.py` (VPC de 3 niveles con subredes CDE aisladas).
- [ ] **Paso 2.3:** Implementar `infra/data_stack.py` (ElastiCache Redis Multi-AZ y Aurora Serverless v2 con KMS).
- [ ] **Paso 2.4:** Implementar `infra/compute_stack.py` (Cluster EKS con NodeGroup Graviton3 ARM64).

### Fase 3: Capa de Dominio Pura (Clean Architecture)
- [ ] **Paso 3.1:** Crear Value Objects `SKU.kt`, `WarehouseId.kt`, `StockSource.kt`, `ReservationStatus.kt`.
- [ ] **Paso 3.2:** Implementar agregado raíz `StockLevel.kt` y entidad `StockReservation.kt`.
- [ ] **Paso 3.3 (TDD Red → Green):** Crear suite de pruebas `StockLevelTest.kt` validando reglas de inmutabilidad y cálculo de stock.
- [ ] **Paso 3.4:** Definir puertos de dominio `StockCachePort.kt` y `ErpStockPort.kt`.

### Fase 4: Capa de Aplicación (Casos de Uso)
- [ ] **Paso 4.1:** Definir DTOs y comandos `StockQueryCommand.kt` y `StockResponseDto.kt`.
- [ ] **Paso 4.2:** Definir interfaz `GetStockUseCase.kt`.
- [ ] **Paso 4.3 (TDD Red):** Escribir `GetStockServiceTest.kt` cubriendo los 4 escenarios (cache hit, cache miss, fallback stale, error 503).
- [ ] **Paso 4.4 (TDD Green):** Implementar `GetStockService.kt` orquestando la resolución de stock y actualización de caché.

### Fase 5: Capa de Infraestructura y Adaptadores
- [ ] **Paso 5.1:** Implementar `RedisStockAdapter.kt` implementando `StockCachePort` con Lettuce (pipeline de claves fresca y stale).
- [ ] **Paso 5.2:** Implementar `SapStockAdapter.kt` implementando `ErpStockPort` con Resilience4j (`@CircuitBreaker` y `@Bulkhead`).
- [ ] **Paso 5.3:** Implementar mappers ACL `SapStockMapper.kt` y cliente HTTP `SapClient.kt`.
- [ ] **Paso 5.4:** Implementar controlador REST `StockController.kt` con endpoint `GET /v1/inventory/stock/{sku}`.
- [ ] **Paso 5.5:** Implementar `GlobalExceptionHandler.kt` retornando RFC 7807 (`application/problem+json`).
- [ ] **Paso 5.6:** Implementar filtros de seguridad y trazabilidad: `SecurityConfig.kt` (OAuth2 JWT) y `CorrelationIdFilter.kt` (MDC SLF4J).
- [ ] **Paso 5.7:** Crear archivos de configuración `application.yml` y `logback-spring.xml` (JSON layout).

### Fase 6: Pruebas de Integración, Arquitectura y Empaquetado
- [ ] **Paso 6.1 (TDD Red → Green):** Implementar `StockControllerTest.kt` validando seguridad, scopes y cabeceras.
- [ ] **Paso 6.2:** Implementar `ArchitectureTest.kt` con ArchUnit verificando que `domain` sea 100% independiente.
- [ ] **Paso 6.3:** Implementar `StockAdapterIntegrationTest.kt` demostrando el slice end-to-end del walking skeleton.
- [ ] **Paso 6.4:** Crear `Dockerfile` multi-stage para JDK 21 optimizado para ARM64 Graviton.

---

## Assumptions & Open Questions

- [assumption] Todos los pasos se ejecutan de manera estricta y progresiva, marcando las casillas correspondientes a medida que se generan los archivos en el espacio de trabajo.
