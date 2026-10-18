## Sources

- [F-Q1] Regulación Colombia: Ley 1581/2012 + enmascaramiento PII.
- [F-Q3] Restricción de costo: optimizar al máximo.
- [F-Q5] Conectividad on-premise: estado desconocido.
- [MR-Q1] Sin middleware previo.
- [MR-Q4] IdP con costo como restricción.
- [desc] Requerimientos no funcionales: latencia p95 < 300ms, disponibilidad 99.9%.

---

## Constraint Register

### Restricciones Absolutas de Diseño (no negociables)

Estas tres restricciones son mandatos de diseño que aplican a todos los stages posteriores sin excepción. Ninguna decisión de arquitectura, implementación o scope puede contradirlas.

| ID | Restricción absoluta | Alcance |
|----|---------------------|---------|
| **CA-01** | **PCI-DSS — Segmentación CDE y enmascaramiento de PII:** Toda comunicación de datos de pago ocurre dentro de un Cardholder Data Environment (CDE) segmentado. El PAN nunca viaja fuera del CDE. Los datos PII se enmascaran en logs en todos los servicios sin excepción. | Toda la plataforma |
| **CA-02** | **Protección de WMS/ERP mediante backpressure:** Los sistemas legacy (ERP, WMS) nunca reciben más carga de la que pueden manejar. Rate limiting por canal, Circuit Breaker y Bulkhead son obligatorios en todos los adaptadores que llamen a sistemas legacy. | Stock Adapter, WMS Adapter, OMS Adapter, CRM Adapter |
| **CA-03** | **Clean Architecture en todos los servicios backend:** La estructura de cada microservicio separa domain → application → infrastructure. Las reglas de negocio no tienen dependencias de frameworks ni de adaptadores externos. Las dependencias apuntan hacia adentro (Dependency Rule). | Order Service, Stock Adapter, Payment Service, Notification Service |

---

### Restricciones Técnicas

| ID | Restricción | Impacto | Origen | Mitigación |
|----|-------------|---------|--------|-----------|
| CT-01 | Latencia p95 < 300ms para stock/precio en canales externos | Alto — criterio de éxito del intent | [desc] | Cache Redis como primera línea de defensa; ERP solo en cache miss |
| CT-02 | Disponibilidad 99.9% para APIs externas (~8.7h downtime/año) | Alto — SLA de marketplace | [desc] | Multi-AZ en EKS, RDS Aurora Multi-AZ, ElastiCache con réplica |
| CT-03 | Estado de conectividad on-premise desconocido | Crítico — bloquea el Stock Adapter | [F-Q5] | Discovery técnico semana 1; alternativa cache-first si no hay conectividad |
| CT-04 | APIs del ERP desconocidas (REST, SOAP, IDoc, RFC) | Alto — determina complejidad del ACL | [MR-Q1] | Discovery técnico semana 1 con equipo ERP |
| CT-05 | Costo de infraestructura debe minimizarse | Medio — restricción de diseño | [F-Q3] | Instancias ARM Graviton, Aurora Serverless v2, Kong OSS, Keycloak OSS |

### Restricciones Regulatorias (Colombia)

| ID | Restricción | Impacto | Norma | Implementación |
|----|-------------|---------|-------|---------------|
| CR-01 | Enmascaramiento de PII en todos los logs de aplicación | Alto — incumplimiento implica sanción SIC | Ley 1581/2012 Art. 17 | Log sanitization middleware en cada servicio; CI/CD scan de PII en logs |
| CR-02 | Finalidad del tratamiento de datos personales declarada y limitada | Medio — datos solo para propósito de la orden | Ley 1581/2012 Art. 4 | Order Service no reutiliza datos; políticas IAM restringen acceso cross-servicio |
| CR-03 | Consentimiento del titular para tratamiento de datos | Medio — AndesRetail debe gestionarlo | Ley 1581/2012 Art. 9 | Fuera del scope técnico; AndesRetail gestiona el consentimiento en su frontend |
| CR-04 | Registro de base de datos ante SIC (RNBD) | Bajo (para Vortexbird) — responsabilidad de AndesRetail | Decreto 1377/2013 Art. 6 | Vortexbird documenta qué datos maneja cada servicio como insumo para el registro |
| CR-05 | Seguridad del dato en tránsito y en reposo | Alto — cifrado obligatorio | Ley 1581/2012 Art. 17h | TLS 1.3 en tránsito; KMS en reposo para RDS, Redis, SQS, Secrets Manager |
| CR-06 | PCI-DSS — scope mínimo por tokenización | Alto — manejo de pagos con tarjeta | PCI-DSS v4.0 | El PAN nunca llega a sistemas de AndesRetail; solo tokens de pago |

### Restricciones Organizacionales

| ID | Restricción | Impacto | Origen | Mitigación |
|----|-------------|---------|--------|-----------|
| CO-01 | Equipo Vortexbird construye todo — sin conocimiento previo del negocio de AndesRetail | Medio — curva de aprendizaje de dominio | [F-Q2] | Sesiones de domain discovery con stakeholders de AndesRetail en semana 1-2 |
| CO-02 | No se conocen los vendors de ERP/WMS ni sus SLAs de soporte | Bajo-Medio — puede afectar discovery técnico | [MR-Q1] | Incluir tiempo de discovery técnico en el roadmap como actividad explícita |

---

## Assumptions & Open Questions

- [assumption] Las restricciones regulatorias de Colombia (Ley 1581/2012) aplican al tratamiento de datos personales de ciudadanos colombianos. Si AndesRetail opera en otros países de LATAM (México, Perú, Chile), aplican regulaciones adicionales que deben evaluarse por separado.
- [assumption] La restricción CT-03 (conectividad on-premise) es el bloqueador de mayor riesgo para el roadmap del MVP. Si el discovery revela que no existe conectividad y que provisionarla toma más de 2 semanas, el alcance del MVP debe ajustarse.
