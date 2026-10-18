## Sources

- [desc] Proyecto API/backend sin UI de usuario final.
- [TF-stack] Kotlin + Spring Boot / Python CDK.
- [CA-03] Clean Architecture.

---

## Design System Mapping — API Platform

Este proyecto no tiene interfaz de usuario para el usuario final. El "design system"
de la plataforma de APIs se compone de los siguientes estándares técnicos que aplican
de forma consistente en todos los servicios.

### Estándares de Diseño de API

| Estándar | Decisión | Razón |
|----------|---------|-------|
| Estilo de API | REST sobre HTTPS | Compatibilidad universal con canales externos; no requiere cliente especial |
| Versionado | URL path (`/v1/`) | Visibilidad explícita; facilita routing en Kong por versión |
| Formato de respuesta | JSON (application/json) | Universal; soportado por todos los canales |
| Formato de fechas | ISO-8601 UTC | Eliminación de ambigüedad de zona horaria |
| Formato de moneda | Integer (centavos) | Evita errores de punto flotante en lógica de negocio |
| Paginación | Cursor-based (para listas futuras) | Mejor rendimiento en datasets grandes que offset/limit |
| Errores | RFC 7807 Problem Details | Estándar industria; consistencia entre servicios |
| Correlación | X-Correlation-ID en todos los endpoints | Trazabilidad end-to-end obligatoria [NFR-08] |
| Idempotencia | Header `Idempotency-Key` en POST | Manejo seguro de reintentos sin duplicados [FR-04] |

### Estándares de Código (Kotlin/Spring Boot)

| Estándar | Decisión |
|----------|---------|
| Result type | `kotlin.Result<T>` o sealed class `Either<Error, T>` para operaciones fallibles |
| Null safety | Aprovechar el sistema de tipos de Kotlin — no usar `!!` (non-null assertion) en código de producción |
| Data classes | Para Value Objects y DTOs de dominio |
| Sealed classes | Para modelar estados de dominio (OrderStatus) y errores de dominio |
| Coroutines | Evaluado en mes 3 si la carga justifica I/O no bloqueante; en MVP se usa threading estándar de Spring |
| Logging | SLF4J + Logback + logstash-logback-encoder (JSON estructurado) |
| Log sanitization | Filtro de Logback que redacta campos PII/sensibles [CA-01] |

### Estándares de IaC (Python CDK)

| Estándar | Decisión |
|----------|---------|
| Formatter | Black (line-length=88) |
| Type hints | Obligatorios en todas las funciones CDK |
| Naming | snake_case para variables/funciones, PascalCase para Constructs |
| Environments | Parámetros de contexto CDK (`cdk.json`) por entorno (dev/staging/prod) |
| RemovalPolicy | `RETAIN` en prod para todos los recursos stateful; `DESTROY` en dev |

---

## Assumptions & Open Questions

- [assumption] Los estándares de diseño de API aplican desde el primer sprint. No son deuda técnica a resolver después — son el contrato con los canales externos desde el día 1.
