## Sources

- [performance-requirements.md] Requerimientos de rendimiento y latencia p95.
- [NFRQ-01] Presupuesto de latencia.
- [tech-stack-decisions.md] Lettuce Redis, Spring Boot 3.2, JDK 21.

---

# Diseño de Rendimiento — `skeleton-stock-query`

Este documento describe los patrones arquitectónicos de rendimiento, estrategias de optimización de memoria y configuración de conexiones implementados en el servicio `stock-price-adapter`.

---

## 1. Estrategia de Caché de Alto Rendimiento (Lettuce + Redis)

Para garantizar latencias menores a 50ms en el 90%+ de las consultas [NFR-01], se implementa un cliente no bloqueante basado en eventos de E/S con Netty y Lettuce.

### Arquitectura de Caché Dual en Pipeline

```
[Invocación Entrada] ──► [Redis Cache Fresco: TTL 60s] ──(Hit < 15ms)──► [200 OK]
                                   │
                                (Miss)
                                   ▼
                       [ERP SAP on-premise]
                                   │
                                (Éxito)
                                   ▼
               [Pipeline Lettuce: Guarda Fresco (60s) + Stale (1h)]
```

### Configuración del Pool de Conexiones Lettuce

```kotlin
// Ilustrativo (≤15 líneas): Configuración de cliente Lettuce no bloqueante
@Bean
fun redisClientConfiguration(): ClientOptions = ClientOptions.builder()
    .socketOptions(SocketOptions.builder().connectTimeout(Duration.ofMillis(100)).build())
    .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(200)))
    .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
    .build()
```

---

## 2. Presupuesto de Latencia y Manejo de Conexiones HTTP

Para asegurar que la llamada al ERP legacy no degrade el SLA total:
- **Pool de Conexiones HTTP (Apache HttpClient 5 / WebClient):**
  - Max conexiones totales: 50.
  - Max conexiones por ruta (SAP host): 20 (alineado al Bulkhead).
  - Tiempo de conexión (Connect Timeout): 100ms.
  - Tiempo de respuesta (Socket/Read Timeout): 250ms estricto.
- **Serialización Rápida:** Jackson Kotlin Module pre-configurado sin reflexión en tiempo de ejecución para reducir el tiempo de CPU por serialización a < 2ms.

---

## 3. Optimizaciones en JVM 21 (ZGC / G1)

- Se habilita el recolector de basura de baja latencia G1GC con pausas máximas de 10ms (`-XX:MaxGCPauseMillis=10 -XX:+UseG1GC`).
- Uso intensivo de `value class` de Kotlin para representar identificadores de dominio (`SKU`, `WarehouseId`) eliminando la sobrecarga de asignación de objetos en el Heap.

---

## Assumptions & Open Questions

- [assumption] Redis opera con compresión interna deshabilitada dado que los objetos de inventario son pequeños (< 500 bytes) y la compresión añadiría sobrecarga innecesaria de CPU.
