## Sources

- [desc] Initial description del proyecto Andes Retail.
- [NFR-01] Latencia p95 <= 300ms stock/precio.
- [NFR-03] Picos 10x absorción.
- [NFR-04] Disponibilidad 99.9%.
- [NFR-06] Cifrado TLS 1.3 y KMS.
- [NFR-07] Scopes y multi-tenant.
- [NFR-08] X-Correlation-ID.
- [NFR-10] Circuit Breaker Resilience4j.
- [NFR-11] Rate limiting Kong.
- [NFR-12] Bulkhead ERP max 20 hilos.
- [CA-01, CA-02, CA-03] Restricciones absolutas.

---

## NFRQ-01. Objetivos de Rendimiento y Presupuesto de Latencia

¿Cómo se distribuye el presupuesto de 300ms de latencia p95 para la consulta de stock?

- **En Caché Hit (90%+ de peticiones):**
  - Tránsito Edge (API Gateway + Kong Ingress + TLS 1.3): <= 25ms.
  - Procesamiento Spring Boot + lectura Redis: <= 15ms.
  - Latencia total p95 esperada en cache hit: <= 40ms.
- **En Caché Miss (Llamada al ERP):**
  - Tránsito Edge: <= 25ms.
  - Procesamiento Adapter + llamadas internas: <= 15ms.
  - Timeout estricto de lectura hacia SAP ERP: <= 250ms.
  - Latencia total p95 esperada en cache miss: <= 290ms.

[Answer]: Presupuesto de latencia cerrado con timeout de 250ms en ERP para asegurar cumplimiento estricto del SLA de 300ms p95.

---

## NFRQ-02. Umbrales de Escalado Automático y Capacidad

¿Cuáles son los parámetros de auto-escalado horizontal (HPA) para los pods de stock en EKS?

- Mínimo de réplicas en EKS: 2 pods distribuidos en 2 zonas de disponibilidad (AZ) para alta disponibilidad.
- Máximo de réplicas: 10 pods en instancias ARM64 Graviton (c7g.medium).
- Métrica de escalado HPA: CPU target average utilization al 70% o latencia p95 > 200ms.

[Answer]: HPA configurado de 2 a 10 réplicas multi-AZ con threshold al 70% de CPU.

---

## NFRQ-03. Políticas de Seguridad Perimetral y Redes

¿Cuáles son los requerimientos de red y seguridad para la Unidad 1?

- Ingress restringido: Solo tráfico HTTPS TLS 1.3 proveniente de AWS API Gateway / Kong.
- Los pods de `stock-price-adapter` residen en subredes privadas sin IP pública.
- ElastiCache Redis reside en subred de base de datos aislada con Security Group permitiendo únicamente puerto 6379 desde el SG del pod.
- Validación de claims de JWT: `iss` (Keycloak), `aud` (andes-retail-api), `exp`, `tenantId`, y scope requerido `inventory:read`.

[Answer]: Aislamiento total en subredes privadas, TLS 1.3 perimetral y validación estricta de claims OAuth2.

---

## Assumptions & Open Questions

- [assumption] Los nodos de EKS operan bajo arquitectura ARM64 (AWS Graviton3) optimizando costos y rendimiento por vCPU.
- [assumption] Las credenciales de conexión a Redis y SAP se inyectan como variables de entorno a través de External Secrets Operator desde AWS Secrets Manager.
