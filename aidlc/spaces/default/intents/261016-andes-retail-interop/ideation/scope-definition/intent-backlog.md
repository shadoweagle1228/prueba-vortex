## Sources

- [desc] Requerimientos funcionales y no funcionales del brief.
- [scope] Workflow-selected scope: `feature`.
- [Q4] Criterio de éxito MVP.
- [CA-01] PCI-DSS — segmentación CDE y enmascaramiento PII.
- [CA-02] Backpressure en adaptadores hacia legados.
- [CA-03] Clean Architecture en servicios backend.
- [F-Q3] Restricción de costo.

---

## Intent Backlog — Andes Retail Interoperability Platform

El backlog está organizado por capacidad de negocio (no por componente técnico).
Cada ítem es una capacidad verificable que puede demostrarse en producción.

---

### BLOQUE 0 — Fundaciones (semana 1-2, prerequisito de todo lo demás)

| ID | Capacidad | Restricciones | Criterio de verificación |
|----|-----------|--------------|------------------------|
| F-00 | Discovery técnico: estado de conectividad on-premise y APIs del ERP | [CA-02] | Documento de discovery firmado por equipo AndesRetail |
| F-01 | VPC con CDE segmentado, subnets privadas y VPC Endpoints AWS | [CA-01][F-Q3] | `terraform plan` / CDK synth sin recursos públicos en el CDE |
| F-02 | EKS Cluster con API Server privado y nodos ARM Graviton | [CA-01][F-Q3] | `kubectl get nodes` desde VPN/bastion; nodos en subnets privadas |
| F-03 | Secrets Manager + External Secrets Operator operacional | [CA-01] | Pod de prueba lee secreto sin credenciales hardcodeadas |
| F-04 | CI/CD pipeline básico (build → test → deploy) | [CA-03] | PR en main dispara pipeline y despliega a dev |

---

### BLOQUE 1 — Seguridad y Autenticación (semana 2-3)

| ID | Capacidad | Restricciones | Criterio de verificación |
|----|-----------|--------------|------------------------|
| S-01 | Keycloak desplegado con realm `retail` y modelo multi-tenant | [CA-01] | Token JWT válido con claims `tenantId`, `scope`, `roles` emitido correctamente |
| S-02 | AWS API Gateway con WAF + validación de JWT | [CA-01] | Request sin JWT → 401; JWT expirado → 401; JWT de otro tenant → 403 |
| S-03 | Kong Ingress con plugins JWT, Rate Limiting y CORS | [CA-01][CA-02] | 101ª request en 1s desde mismo consumer → 429 |
| S-04 | Log sanitization middleware activo en todos los servicios | [CA-01][F-Q1] | Log de creación de orden no contiene campos PII (email, teléfono, dirección) ni token de pago completo |
| S-05 | Scan de PII en CI/CD (falla build si detecta PAN/PII en logs) | [CA-01][F-Q1] | Commit con `log.info("email: " + email)` → pipeline falla |

---

### BLOQUE 2 — Consulta de Stock y Precio (semana 3-4)

| ID | Capacidad | Restricciones | Criterio de verificación |
|----|-----------|--------------|------------------------|
| SP-01 | Stock Adapter con Cache Redis (cache-first) | [CA-02][CA-03] | Consulta con cache hit → p95 < 50ms |
| SP-02 | Stock Adapter con Circuit Breaker (Resilience4j) hacia ERP | [CA-02][CA-03] | ERP simulado con latencia > 300ms → Circuit Breaker abre; respuesta retorna cache stale |
| SP-03 | Stock Adapter con Bulkhead (thread pool aislado) | [CA-02][CA-03] | 200 requests simultáneos al Stock Adapter no impactan el thread pool del Order Service |
| SP-04 | `GET /v1/inventory/stock/{sku}` end-to-end con Auth | [CA-01][CA-02] | Token válido con scope `inventory:read` → 200 con stock; sin scope → 403 |
| SP-05 | `GET /v1/catalog/price/{sku}` end-to-end con Auth | [CA-01][CA-02] | Token válido con scope `catalog:read` → 200 con precio por canal |
| SP-06 | Latencia p95 < 300ms para stock/precio con cache activo | [CA-02] | Load test: 100 req/s durante 5 min → p95 ≤ 300ms en percentil |

---

### BLOQUE 3 — Crear Orden (semana 4-5) [CRÍTICO — criterio de éxito MVP]

| ID | Capacidad | Restricciones | Criterio de verificación |
|----|-----------|--------------|------------------------|
| O-01 | `POST /v1/orders` con validación de stock y pago (happy path) | [CA-01][CA-02][CA-03] | Orden creada en < 2s end-to-end con 201 Created y orderId |
| O-02 | Idempotencia: segunda llamada con mismo Idempotency-Key retorna respuesta original | [CA-03] | Dos requests idénticos con mismo header → ambos 201, segundo con `Idempotency-Replay: true` |
| O-03 | Compensación (Saga): si el pago falla, el stock reservado se libera | [CA-02][CA-03] | Pago rechazado simulado → stock del SKU vuelve al valor previo en el ERP/cache |
| O-04 | `GET /v1/orders/{orderId}` consulta estado | [CA-01][CA-03] | Token del canal que creó la orden → 200; token de otro canal → 403 |
| O-05 | Evento `order.confirmed` publicado en EventBridge después de crear orden exitosa | [CA-03] | CloudWatch muestra evento en el bus `order-events` con schema correcto |
| O-06 | Log de creación de orden no contiene PII ni token de pago completo | [CA-01][F-Q1] | Revisión de log en CloudWatch: ningún campo sensible en claro |

---

### BLOQUE 4 — Webhooks y Notificaciones (semana 5-6)

| ID | Capacidad | Restricciones | Criterio de verificación |
|----|-----------|--------------|------------------------|
| W-01 | Webhook `payment.approved` entregado al canal externo | [CA-01] | Simulación de pago aprobado → canal externo recibe POST en su endpoint de webhook en < 5s |
| W-02 | Webhook `payment.rejected` entregado al canal externo | [CA-01] | Pago rechazado simulado → canal externo recibe evento con razón del rechazo |
| W-03 | DLQ activa: webhook fallido reintentado 3 veces antes de ir a DLQ | [CA-02] | Endpoint de webhook del canal devuelve 500 → 3 reintentos → mensaje en DLQ → alarma CloudWatch |
| W-04 | Webhook `shipment.started` (disponible en mes 3 con WMS Adapter) | [CA-02] | Fuera del MVP — placeholder en el Event Schema Registry |

---

### BLOQUE 5 — Observabilidad MVP (semana 5-6)

| ID | Capacidad | Restricciones | Criterio de verificación |
|----|-----------|--------------|------------------------|
| OB-01 | 10 métricas clave visibles en Grafana | — | Dashboard activo con todas las métricas con datos reales |
| OB-02 | Alarma CloudWatch: Circuit Breaker ERP abierto > 60s | [CA-02] | ERP simulado offline → alarma activa en < 2 min |
| OB-03 | Alarma CloudWatch: DLQ con mensajes | — | Webhook fallido → alarma activa en < 2 min |
| OB-04 | Correlation-ID propagado en todos los servicios | — | Log de un request en Order Service muestra el mismo correlationId que el log del Stock Adapter |

---

### BACKLOG MES 3

| ID | Capacidad |
|----|-----------|
| M3-01 | WMS Adapter con backpressure (fulfillment asíncrono via EventBridge → SQS → WMS) |
| M3-02 | CRM Adapter (sincronización de cliente en alta) |
| M3-03 | Onboarding de segundo canal (marketplace piloto) |
| M3-04 | Developer Portal con documentación de APIs |
| M3-05 | Pruebas de carga al 5x con AWS FIS |

---

### BACKLOG MES 6

| ID | Capacidad |
|----|-----------|
| M6-01 | Multi-AZ con failover probado |
| M6-02 | Canary deployments con Argo Rollouts |
| M6-03 | SLOs con Error Budget |
| M6-04 | DR drill documentado |
| M6-05 | Evaluación multi-región |

---

## Assumptions & Open Questions

- [assumption] El backlog asume que el discovery técnico (F-00) no revela bloqueos de conectividad que requieran más de 1 semana de resolución. Si F-00 revela un bloqueo mayor, los bloques SP y O se desplazan 1-3 semanas.
- [assumption] Los criterios de verificación son la base para las pruebas de aceptación que se detallarán en la fase de inception (Requirements Analysis y User Stories).
