## Sources

- [desc] Initial description del proyecto Andes Retail.
- [NFR-03] Picos 10x absorción sin tumbar ERP.
- [NFR-11] Rate limiting por canal.
- [CA-02] Backpressure hacia legados.

---

# Requerimientos de Escalabilidad — `skeleton-stock-query`

Este documento describe los requerimientos de elasticidad, manejo de carga máxima y absorción de picos de demanda para el servicio de consulta de inventario.

---

## 1. Comportamiento ante Picos de Demanda (Picos 10x)

- **Escenario de Carga Extrema:** Eventos comerciales especiales (CyberLunes, BlackFriday) con tráfico de hasta 10 veces el volumen nominal (1.000 req/s totales).
- **Estrategia de Absorción en 3 Capas:**
  1. **Capa 1 — Perímetro (Kong Ingress):** Rate limiting estricto por `tenantId` (100 req/s nominal, ráfagas de hasta 150 req/s). Peticiones excedentes reciben `429 Too Many Requests` protegiendo la infraestructura interna [NFR-11].
  2. **Capa 2 — Memoria (Redis Cache):** Con tasa de acierto del 90%+, 900 de cada 1.000 peticiones se resuelven en memoria con latencias menores a 50ms.
  3. **Capa 3 — Frontera Legacy (Bulkhead ERP):** El adaptador restringe a un máximo de 20 llamadas simultáneas hacia el ERP SAP, amortiguando cualquier avalancha de tráfico y conmutando el exceso a *stale cache* [NFR-12, CA-02].

---

## 2. Escalabilidad Horizontal en Kubernetes (EKS HPA)

- **Horizontal Pod Autoscaler (HPA):**
  - Mínimo de pods: 2 (distribuidos en AZ `us-east-1a` y `us-east-1b`).
  - Máximo de pods: 10 réplicas.
  - Regla de disparo: Utilización promedio de CPU > 70% o tasa de peticiones > 120 req/s por pod.
  - Tiempo de estabilización de escala ascendente (scale-up): 15 segundos.
  - Tiempo de enfriamiento para escala descendente (scale-down): 300 segundos (para evitar oscilaciones o *flapping*).

---

## 3. Escalabilidad de la Capa de Datos (ElastiCache Redis)

- Configuración Redis Cluster multi-AZ con 1 nodo primario y 1 réplica de lectura con *automatic failover*.
- Tipo de instancia inicial: `cache.t4g.medium` (Graviton ARM64) escalable verticalmente a `cache.m7g.large` sin interrupción de servicio.

---

## Assumptions & Open Questions

- [assumption] EKS Cluster Autoscaler / Karpenter se encuentra habilitado a nivel de cluster para aprovisionar nodos EC2 adicionales si los pods saturan los nodos existentes.
