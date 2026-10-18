## Sources

- [desc] Initial description del proyecto Andes Retail.
- [NFR-04] Disponibilidad 99.9%.
- [NFR-10] Circuit Breaker en adaptadores legacy.
- [NFR-12] Backpressure y Bulkhead.
- [CA-02] Restricción absoluta backpressure.
- [rules.md] Reglas BR-STK-01 a BR-STK-06.

---

# Requerimientos de Confiabilidad y Resiliencia — `skeleton-stock-query`

Este documento especifica las garantías de disponibilidad, tolerancia a fallos, recuperación automática y políticas de degradación graciosa para el adaptador de stock.

---

## 1. Disponibilidad de Servicio (SLA / SLO)

- **Objetivo de Disponibilidad (SLA):** 99.9% de uptime medido mensualmente en el endpoint `GET /v1/inventory/stock/{sku}` (downtime no planificado máximo: 43.8 minutos/mes).
- **Service Level Objective (SLO):** 99.95% de respuestas exitosas (códigos 200, o 503 controlados bajo degradación) sobre el total de peticiones válidas.
- **Topología de Alta Disponibilidad:**
  - Pods desplegados con `topologySpreadConstraints` forzando distribución balanceada en al menos dos zonas de disponibilidad (Multi-AZ).
  - PodDisruptionBudget (PDB) configurado con `minAvailable: 1` para garantizar operatividad durante mantenimientos de nodos en EKS.

---

## 2. Política de Circuit Breaker (Resilience4j) [NFR-10]

| Parámetro | Valor de Configuración | Justificación Técnica |
|---|---|---|
| **Tipo de Ventana** | Conteo de llamadas (Count-based) | Respuesta determinista ante ráfagas de fallos |
| **Tamaño de Ventana Deslizante** | 30 llamadas | Suficiente para descartar anomalías transitorias |
| **Umbral de Tasa de Fallo** | 50% | Si 15 de 30 llamadas fallan o superan 250ms, el circuito abre |
| **Duración en Estado OPEN** | 60 segundos | Permite tiempo de recuperación al ERP on-premise |
| **Llamadas de Prueba en HALF_OPEN** | 5 llamadas consecutivas | Verifica si el ERP recuperó su salud de forma estable |
| **Timeout de Conexión** | 100ms | Fail-fast ante corte de enlace de red |
| **Timeout de Lectura** | 250ms | Salvaguarda del SLA de 300ms p95 |

---

## 3. Política de Degradación Controlada y Fail-Closed

1. **Degradación Graciosa (Stale Cache):**
   - Cuando el Circuit Breaker conmuta a `OPEN`, el sistema busca en Redis la copia de contingencia generada en la última sincronización exitosa (TTL de hasta 60 minutos).
   - La respuesta se entrega con código `200 OK` y cabecera HTTP `X-Stock-Source: stale-cache`.
2. **Fail-Closed Estricto [BR-STK-04]:**
   - Si no existe copia en caché (e.g. SKU nuevo consultado durante la caída del ERP), el sistema responde con `503 Service Unavailable`, cabecera `Retry-After: 5` y payload RFC 7807 (`ProblemDetails`).
   - Queda terminantemente prohibido asumir stock inventado o responder valores no confirmados.

---

## 4. Política de Aislamiento de Hilos (Bulkhead) [NFR-12]

- **Pool Aislado:** Máximo 20 hilos concurrentes dedicados exclusivamente a la comunicación saliente con el ERP.
- **Cola de Espera:** Máximo 10 peticiones encoladas por hasta 100ms. Si se satura, las peticiones adicionales conmutan inmediatamente a fallback sin bloquear el hilo del despachador HTTP.

---

## Assumptions & Open Questions

- [assumption] Health checks de Kubernetes (`livenessProbe` en `/actuator/health/liveness` y `readinessProbe` en `/actuator/health/readiness`) desacoplados del estado del ERP para evitar reinicios en cascada de pods cuando el ERP esté caído.
