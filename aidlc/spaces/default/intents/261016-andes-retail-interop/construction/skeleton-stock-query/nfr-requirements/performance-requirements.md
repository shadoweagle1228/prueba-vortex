## Sources

- [desc] Initial description del proyecto Andes Retail.
- [NFR-01] Latencia p95 <= 300ms stock.
- [NFR-03] Picos 10x absorción.
- [CA-02] Restricción absoluta backpressure en adaptadores legacy.
- [NFRQ-01] Presupuesto de latencia en nfr-requirements-questions.md.

---

# Requerimientos de Rendimiento — `skeleton-stock-query`

Este documento establece las metas cuantificables de latencia, throughput y eficiencia de recursos para el microservicio de consulta de existencias (`stock-price-adapter`).

---

## 1. Metas Cuantificables de Latencia

| Métrica | Objetivo | Percentil | Condición de Medición | Criterio Pass/Fail |
|---|---|---|---|---|
| **Latencia Cache Hit** | $\le 50\text{ms}$ | p95 | Tráfico normal (100 req/s), dato en Redis | Pass: p95 $\le 50\text{ms}$ en k6 |
| **Latencia Total End-to-End** | $\le 300\text{ms}$ | p95 | Tráfico normal (100 req/s) medido en Gateway | Pass: p95 $\le 300\text{ms}$ sostenido |
| **Latencia Stale Cache** | $\le 60\text{ms}$ | p95 | Circuit Breaker OPEN, fallback activo | Pass: p95 $\le 60\text{ms}$ |
| **Timeout de Conexión ERP** | $\le 100\text{ms}$ | Máximo | Apertura de socket hacia SAP | Falla rápida ante corte de red |
| **Timeout de Lectura ERP** | $\le 250\text{ms}$ | Máximo | Espera de respuesta SAP | Conmuta a fallback si supera 250ms |

---

## 2. Capacidad de Throughput y Concurrencia

- **Capacidad Nominal Baseline:** 100 req/s continuos por pod de EKS.
- **Tasa de Acierto de Caché (Cache Hit Ratio):** $\ge 90\%$ bajo catálogo activo de productos.
- **Régimen de Picos:** Soporte de hasta 1.000 req/s en el cluster mediante escalado horizontal (HPA) y absorción en Redis.
- **Límite Hacia el ERP:** Máximo 20 consultas concurrentes hacia el ERP legacy gobernadas por Bulkhead [NFR-12, CA-02].

---

## 3. Presupuesto de Recursos por Contenedor (Resource Budgets)

| Recurso | Solicitud (Requests) | Límite (Limits) | Umbral de Alerta |
|---|---|---|---|
| **CPU** | 250m vCPU (Graviton3 ARM64) | 1000m vCPU | CPU > 70% por más de 2 min |
| **Memoria RAM** | 512 MiB | 1024 MiB | Heap JVM > 80% sostenido |
| **Conexiones Redis** | Pool de 50 conexiones Lettuce | Max 100 conexiones | Saturación de pool > 85% |

---

## Assumptions & Open Questions

- [assumption] Las pruebas de verificación de rendimiento se ejecutan mediante scripts k6 automatizados en el stage `load-validation` (Bolt 8) y durante las pruebas locales del walking skeleton.
