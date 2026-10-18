## Sources

- [functional-spec.md] Especificación funcional y puertos.
- [CA-03] Clean Architecture (domain, application, infrastructure).
- [tech-stack-decisions.md] Kotlin 1.9, Spring Boot 3.2.

---

# Componentes Lógicos y Paquetes — `skeleton-stock-query`

Este documento define la estructura de paquetes y la distribución de componentes lógicos para el microservicio `stock-price-adapter` siguiendo la **Dependency Rule** de Clean Architecture [CA-03].

---

## 1. Estructura de Paquetes en Kotlin

```
com.andesretail.stock
├── domain                           # CAPA DE DOMINIO (Cero dependencias externas)
│   ├── model
│   │   ├── StockLevel.kt            # Agregado raíz
│   │   ├── StockReservation.kt      # Entidad de reserva
│   │   ├── SKU.kt                   # Value Object inline
│   │   ├── WarehouseId.kt           # Value Object inline
│   │   └── StockSource.kt           # Enum (CACHE, ERP, STALE_CACHE)
│   └── port
│       ├── StockCachePort.kt        # Puerto para caché Redis
│       └── ErpStockPort.kt          # Puerto para integración ERP
│
├── application                      # CAPA DE APLICACIÓN (Casos de uso)
│   ├── usecase
│   │   ├── GetStockUseCase.kt       # Interfaz del caso de uso
│   │   └── GetStockService.kt       # Implementación de orquestación de stock
│   └── dto
│       ├── StockQueryCommand.kt     # DTO de entrada al caso de uso
│       └── StockResponseDto.kt      # DTO de salida del caso de uso
│
└── infrastructure                   # CAPA DE INFRAESTRUCTURA (Frameworks y adaptadores)
    ├── adapter
    │   ├── in.rest
    │   │   ├── StockController.kt   # Controlador REST v1
    │   │   └── GlobalExceptionHandler.kt # Manejador de errores RFC 7807
    │   ├── out.cache
    │   │   └── RedisStockAdapter.kt # Implementación de StockCachePort con Lettuce
    │   └── out.erp
    │       ├── SapStockAdapter.kt   # Implementación de ErpStockPort con Resilience4j
    │       ├── SapClient.kt         # Cliente HTTP hacia SAP BAPI/REST
    │       └── SapStockMapper.kt    # ACL: Mapeo de estructuras SAP a Dominio
    ├── config
    │   ├── SecurityConfig.kt        # Spring Security OAuth2 Resource Server
    │   ├── RedisConfig.kt           # Configuración de pool y serializadores Redis
    │   ├── ResilienceConfig.kt      # Listeners y métricas de Resilience4j
    │   └── CorrelationIdFilter.kt   # Filtro HTTP para MDC de SLF4J
    └── StockApplication.kt          # Main Spring Boot Application
```

---

## 2. Verificación de Arquitectura Limpia (ArchUnit Rule)

Para cumplir estrictamente con [CA-03], el pipeline de CI ejecuta la siguiente regla de ArchUnit:

```kotlin
// Ilustrativo (≤15 líneas): Regla de Dependencia de Clean Architecture con ArchUnit
@ArchTest
val domainShouldNotDependOnOuterLayers: ArchRule = noClasses()
    .that().resideInAPackage("..domain..")
    .should().dependOnClassesThat()
    .resideInAnyPackage("..application..", "..infrastructure..", "org.springframework..")
```

---

## Assumptions & Open Questions

- [assumption] La estructura de paquetes es validada en tiempo de compilación y en CI mediante ArchUnit; cualquier violación rompe el build inmediatamente [project.md].
