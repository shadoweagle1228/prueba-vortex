## Sources

- [desc] Initial description del proyecto Andes Retail.
- [NFR-08] Auditoría end-to-end con correlation-id.
- [NFR-04, NFR-10] Métricas operacionales y alarmas.
- [CA-01] Enmascaramiento PII en logs.

---

# Requerimientos de Observabilidad — `skeleton-stock-query`

Este documento define la telemetría, métricas cuantitativas, formato de logs y reglas de alertamiento requeridas para el servicio `stock-price-adapter`.

---

## 1. Métricas de Rendimiento y Salud (Prometheus Micrometer)

| Métrica | Tipo | Etiquetas (Tags) | Descripción / Propósito |
|---|---|---|---|
| `stock_query_duration_seconds` | Histogram | `source` (cache, erp, stale), `status` | Distribución de latencias de consulta con buckets a 10ms, 25ms, 50ms, 100ms, 250ms, 500ms. |
| `cache_requests_total` | Counter | `result` (hit, miss) | Ratio de acierto en Redis; cálculo de Cache Hit Ratio ($\ge 90\%$). |
| `erp_circuit_breaker_state` | Gauge | `state` (closed=0, open=1, half_open=2) | Estado actual del Circuit Breaker hacia SAP ERP en tiempo real. |
| `erp_circuit_breaker_calls_total` | Counter | `kind` (successful, failed, not_permitted) | Desglose de llamadas salientes al ERP y rechazos automáticos. |
| `erp_bulkhead_available_concurrent_calls` | Gauge | — | Hilos libres en el pool del Bulkhead (0 a 20). |
| `http_server_requests_seconds` | Summary | `uri`, `status`, `method` | Métricas estándar de HTTP de Spring Actuator. |

---

## 2. Registro Estructurado de Logs y Trazabilidad [NFR-08]

- **Formato Estándar:** JSON estructurado en una sola línea por evento (Logback con Logstash Logback Encoder) exportado a `stdout`.
- **Campos Mandatorios en cada línea de log:**
  - `timestamp`: Marca de tiempo ISO-8601 en UTC (`2026-10-17T21:30:00.123Z`).
  - `level`: Nivel de severidad (`INFO`, `WARN`, `ERROR`).
  - `service`: Identificador del microservicio (`stock-price-adapter`).
  - `correlationId`: Identificador UUID único propagado desde la cabecera `X-Correlation-ID` [NFR-08].
  - `tenantId`: Identificador del canal llamante obtenido del JWT.
  - `sku`: Código del producto consultado (dato técnico no confidencial).
  - `source`: Origen de resolución (`cache`, `erp`, `stale-cache`).
  - `durationMs`: Tiempo total de ejecución de la solicitud.
  - `message`: Descripción del evento.
- **Sanitización de Datos [CA-01]:** Terminantemente prohibido registrar tokens JWT en claro, contraseñas, o datos PII.

---

## 3. Alarmas de Amazon CloudWatch

| Alarma | Condición de Disparo | Severidad | Acción Automatizada |
|---|---|---|---|
| **ALM-CB-OPEN** | `erp_circuit_breaker_state == 1` por > 1 minuto | ALTA | Notificación inmediata a canal Slack/PagerDuty de operaciones. |
| **ALM-LAT-P95** | Latencia p95 > 300ms en ventana de 5 minutos | MEDIA | Alerta de degradación de servicio para revisión de caché. |
| **ALM-5XX-ERR** | Tasa de errores HTTP 5xx > 1% en 3 minutos | ALTA | Alerta de incidencia técnica activa. |
| **ALM-REDIS-SAT** | Uso de memoria Redis > 80% o CPU Redis > 75% | MEDIA | Alerta de capacidad para evaluación de escalado vertical. |

---

## Assumptions & Open Questions

- [assumption] Los logs enviados a `stdout` son recolectados por AWS FluentBit / CloudWatch Container Insights en el cluster EKS.
