## Sources

- [desc] Initial description y requerimientos no funcionales.
- [F-Q1] Regulación Colombia Ley 1581/2012.
- [F-Q3] Restricción de costo máxima optimización.
- [F-Q5] Conectividad on-premise desconocida.
- [MR-Q1] Sin middleware previo — integraciones custom actuales.
- [MR-Q4] Keycloak como IdP recomendado.
- [Q1 intent] Dolor principal: incidentes en picos de tráfico.
- [Q8 intent] Modelo multi-tenant por canal.

---

## RAID Log

### Riesgos (Risks)

| ID | Riesgo | Probabilidad | Impacto | Estrategia | Owner |
|----|--------|-------------|---------|-----------|-------|
| R-01 | Conectividad on-premise no existe o tiene latencia inaceptable. Sin ella, el Stock Adapter no puede cumplir p95 < 300ms. | Alta (estado desconocido) | Crítico | Discovery semana 1. Alternativa: modo cache-first con sincronización batch cada 15min. Ajustar SLA de stock a "consistencia eventual" si aplica. | Vortexbird + AndesRetail IT |
| R-02 | APIs del ERP son batch/IDoc — no tienen interfaz REST/SOAP disponible. Requiere construir una capa de exposición en el ERP antes de poder integrar. | Media | Alto | Discovery semana 1 con vendor ERP. Si aplica, provisionar 2-3 semanas adicionales para construir la capa de exposición. Evaluar SAP API Management o middleware ligero. | Equipo ERP AndesRetail |
| R-03 | Cascada de fallos en picos 10x: un canal externo con tráfico anómalo satura el Stock Adapter y colapsa el ERP. | Media (ocurrió antes según intent) | Crítico | Rate limiting por canal en Kong (CT-01). Circuit Breaker en Stock Adapter (Resilience4j). Cache Redis como barrera primaria. Load test antes de picos programados. | Vortexbird |
| R-04 | Fuga de PII en logs durante desarrollo (desarrollador agrega log de debug con datos de cliente). | Media | Alto (sanción SIC) | Log sanitization middleware desde sprint 1 — no es una feature de hardening posterior. CI/CD scan automatizado de patrones PII (regex). Política de logs: INFO en prod, DEBUG deshabilitado. | Vortexbird |
| R-05 | Complejidad operacional de Keycloak subestimada: el equipo de AndesRetail no puede absorber la operación del IdP post-entrega. | Media | Medio | Documentar runbook operacional completo. Considerar migración a AWS Cognito en mes 6 si el equipo no absorbe la operación. Terraform provider para toda la configuración de Keycloak como IaC. | Vortexbird |
| R-06 | Inconsistencia de datos de stock entre cache Redis y ERP durante picos: el cache stale muestra stock disponible que ya fue consumido. | Alta (escenario de pico) | Medio | Implementar "stock reservado" en el ACL del ERP antes de confirmar la orden. DLQ + reconciliación nocturna para detectar divergencias. Alertas cuando divergencia > 5%. | Vortexbird |
| R-07 | Vendor lock-in en AWS API Gateway: si el volumen escala a >200M req/mes, el costo variable se vuelve prohibitivo y la migración a Kong es costosa. | Baja (MVP scope) | Medio | Diseñar el contrato de API independiente del gateway (OpenAPI spec first). Revisión de costo en mes 3. La spec es el activo, no el gateway. | Vortexbird |

---

### Supuestos (Assumptions)

| ID | Supuesto | Validación | Fecha límite |
|----|----------|-----------|-------------|
| A-01 | El ERP tiene alguna forma de interfaz para consultar stock en tiempo (cuasi-)real, aunque sea SOAP/RFC | Discovery técnico semana 1 | Semana 1 |
| A-02 | La pasarela de pagos actual de AndesRetail soporta tokenización — el PAN nunca llega a los sistemas de AndesRetail | Confirmar con el equipo de pagos de AndesRetail | Semana 1 |
| A-03 | El equipo de AndesRetail puede participar en sesiones técnicas de discovery (ERP, WMS, OMS, CRM) durante las primeras 2 semanas | Confirmar disponibilidad con el CTO de AndesRetail | Inicio del proyecto |
| A-04 | La cuenta AWS existente tiene permisos suficientes para crear EKS, RDS, ElastiCache, EventBridge, Secrets Manager | Verificar con el equipo de IT de AndesRetail semana 1 | Semana 1 |
| A-05 | AndesRetail está en Colombia y la regulación aplicable es Ley 1581/2012. Si opera en otros países de LATAM, aplican regulaciones adicionales | Confirmar con AndesRetail el alcance geográfico | Inicio del proyecto |
| A-06 | El costo mensual estimado de AWS (~$275-295/mes en MVP) es aceptable para AndesRetail | Presentar estimación al PO de AndesRetail | Semana 1 |

---

### Problemas Activos (Issues)

| ID | Problema | Estado | Acción requerida |
|----|----------|--------|-----------------|
| I-01 | Estado de conectividad on-premise desconocido — bloquea el diseño definitivo del Stock Adapter | Abierto | Discovery técnico semana 1. Bloqueador del roadmap si no se resuelve. |
| I-02 | Interfaces de ERP desconocidas — no se puede estimar el esfuerzo del ACL | Abierto | Sesión técnica con equipo ERP semana 1. |
| I-03 | Marco regulatorio del proyecto ajustado (Colombia, no Chile) — los artefactos previos mencionaban Chile | Resuelto | Corrección aplicada en todos los artefactos de feasibility. Pendiente verificar intent-statement.md para alinear la referencia geográfica. |

---

### Dependencias (Dependencies)

| ID | Dependencia | Tipo | Criticidad | Estado |
|----|-------------|------|-----------|--------|
| D-01 | Conectividad on-premise → AWS (Direct Connect o VPN) | Externa — infraestructura AndesRetail | Crítica | Desconocida |
| D-02 | Acceso técnico a APIs/interfaces del ERP on-premise | Externa — vendor ERP | Crítica | Desconocida |
| D-03 | Disponibilidad del equipo técnico de AndesRetail para discovery | Organizacional | Alta | Sin confirmar |
| D-04 | Permisos IAM en la cuenta AWS de AndesRetail para crear los recursos del stack | Técnica — cuenta AWS AndesRetail | Alta | Sin verificar |
| D-05 | Contrato con el PSP/pasarela de pagos que confirme la tokenización | Externa — PSP | Alta | Asumida como existente |
| D-06 | Definición del primer marketplace/canal piloto para el MVP | Negocio — AndesRetail | Media | Pendiente decisión de PO |

---

## Assumptions & Open Questions

- [assumption] El RAID Log se actualizará al inicio de cada sprint con el estado de los riesgos e issues abiertos. Los issues I-01 e I-02 son bloqueadores del diseño detallado y deben resolverse antes del fin de la semana 1.
