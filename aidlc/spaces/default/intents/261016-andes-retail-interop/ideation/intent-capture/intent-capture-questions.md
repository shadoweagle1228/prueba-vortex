## Sources

- [desc] Initial description: "Diseño de arquitectura completa de interoperabilidad para modernización del ecosistema Andes Retail: capa de integración entre sistemas legacy (ERP, WMS, OMS, CRM) y canales digitales (ecommerce, marketplaces, pasarela de pagos) sobre AWS EKS con enfoque Security-First PCI-DSS, Clean Architecture, resiliencia cloud-native y EDA"
- [scope] Workflow-selected scope: `feature`.

---

## Q1. ¿Cuál es el dolor de negocio más urgente que motiva esta iniciativa hoy?

El brief describe varios problemas simultáneos. Necesitamos priorizar cuál genera
más impacto económico o de riesgo en este momento.

```
A. Incidentes recurrentes en picos de tráfico (promos, CyberDay) que generan pérdidas de venta
B. Inconsistencias de stock/precios entre canales que generan devoluciones y reclamos
C. Riesgo de seguridad o cumplimiento normativo con los datos de pago y PII
D. Imposibilidad de integrar nuevos canales (marketplaces) por la rigidez actual
X. Otro (especificar)
```

[Answer]: A. Incidentes recurrentes en picos de tráfico (promos, CyberDay) que generan pérdidas de venta

---

## Q2. ¿Qué sistemas legacy tienen APIs técnicamente disponibles hoy?

Esto determina si la capa de interoperabilidad adapta interfaces existentes
o si primero hay que construir APIs en los sistemas legacy.

```
A. Todos (ERP, WMS, OMS, CRM) tienen APIs REST o SOAP disponibles
B. Algunos tienen APIs; otros solo interfaces de archivo o batch (FTP, IDoc)
C. Ninguno tiene APIs expuestas — todo es batch/IDoc/RFC/base de datos directa
D. No se conoce con certeza — se necesita un discovery técnico en las primeras semanas
X. Otro (especificar)
```

[Answer]: D. No se conoce con certeza — se necesita un discovery técnico en las primeras semanas

---

## Q3. ¿Cómo se maneja actualmente el dato de tarjeta (PAN) en el flujo de pago?

Define el scope real de cumplimiento PCI-DSS y la estrategia de tokenización.

```
A. La pasarela de pagos ya tokeniza — el PAN nunca llega a los sistemas de AndesRetail
B. El PAN se procesa en sistemas propios de AndesRetail (scope PCI total)
C. En transición hacia tokenización pero aún hay flujos que manejan PAN
D. Solo se redirige al PSP (no hay integración directa de pago)
X. Otro (especificar)
```

[Answer]: A. La pasarela de pagos ya tokeniza — el PAN nunca llega a los sistemas de AndesRetail

---

## Q4. ¿Qué define el éxito de la primera versión (MVP)?

El criterio de éxito ancla el scope del MVP y evita el scope creep.

```
A. Un canal digital integrado end-to-end funcionando en producción (orden → stock → pago → confirmación)
B. Eliminación de las integraciones punto a punto entre al menos dos sistemas legacy
C. Responder a picos 10x sin degradación de los sistemas legacy
D. Los tres anteriores funcionando simultáneamente
X. Otro (especificar)
```

[Answer]: D. Los tres anteriores funcionando simultáneamente

---

## Q5. ¿Quién tiene autoridad para aprobar decisiones de arquitectura en el proyecto?

Define el proceso de toma de decisiones y el nivel de autonomía del equipo técnico.

```
A. Arquitecto/a Senior de Vortexbird con autonomía total
B. Decisiones compartidas entre Vortexbird y el equipo técnico de AndesRetail
C. Existe un Comité de Arquitectura en AndesRetail con proceso formal de aprobación
D. El CTO de AndesRetail aprueba las decisiones de arquitectura directamente
X. Otro (especificar)
```

[Answer]: B. Decisiones compartidas entre Vortexbird y el equipo técnico de AndesRetail

---

## Q6. ¿Qué queda explícitamente fuera del alcance de esta primera entrega?

Definir los límites del scope feature evita compromisos no viables en el MVP.

```
A. Arquitectura multi-región y DR geográfico
B. Integración con todos los marketplaces (se hace uno piloto)
C. Certificación PCI-DSS formal con QSA (el diseño queda listo, no la certificación)
D. Los tres anteriores quedan fuera del scope de esta entrega
X. Otro (especificar — puedes indicar varios)
```

[Answer]: D. Los tres anteriores quedan fuera del scope de esta entrega

---

## Q7. ¿Cuál es el estado actual de la infraestructura AWS y el equipo de plataforma?

Determina el punto de partida real y el esfuerzo de setup de infraestructura.

```
A. Cuenta AWS existente con equipo DevOps/SRE maduro y prácticas CI/CD establecidas
B. Cuenta AWS existente, equipo con experiencia básica, CI/CD incipiente
C. Todo desde cero — cuenta, equipo y prácticas de plataforma
D. Infraestructura on-premise solamente, AWS es un destino nuevo para AndesRetail
X. Otro (especificar)
```

[Answer]: B. Cuenta AWS existente, equipo con experiencia básica, CI/CD incipiente

---

## Q8. ¿Cuál es el modelo multi-tenant requerido para los canales externos?

Define la granularidad de los controles de autorización y rate limiting por canal.

```
A. Cada marketplace/canal tiene sus propias credenciales, scopes y límites de rate
B. Los canales se agrupan por tipo (marketplace vs. e-commerce propio) con políticas por grupo
C. Todos los canales externos comparten el mismo nivel de acceso y límites
D. El modelo de tenancy no está definido — se necesita trabajarlo con AndesRetail
X. Otro (especificar)
```

[Answer]: A. Cada marketplace/canal tiene sus propias credenciales, scopes y límites de rate

---

## Assumptions & Open Questions

- [assumption] Se asume que la infraestructura objetivo es AWS + EKS + Kong + Keycloak según el brief, aunque las herramientas específicas no son impuestas y pueden ajustarse en el discovery técnico.
- [assumption] Se asume conectividad de red entre el datacenter on-premise y AWS (Direct Connect o VPN Site-to-Site). Si no existe, el costo y tiempo de provisión debe considerarse en el roadmap.
- [assumption] Se asume que el equipo de AndesRetail puede destinar al menos un representante técnico de cada sistema legacy (ERP, WMS) para el discovery de APIs en las primeras dos semanas.
- [assumption] Se asume que la latencia p95 < 300ms aplica a las consultas de stock/precio desde canales externos, y que el flujo de creación de orden tiene un SLA diferenciado (< 2s end-to-end incluido el pago).
- [assumption] Se asume que el modelo de datos de producto (catálogo, SKU, precio) tiene una representación canónica en el ERP que sirve como source of truth. Si OMS y WMS tienen modelos divergentes, el diseño del ACL deberá contemplarlo.

---

## Summary Confirmation

El resumen consolidado del Intent Capture fue presentado y validado por el usuario.

Does this all look correct?

Options: Looks correct, Request changes

[Answer]:
