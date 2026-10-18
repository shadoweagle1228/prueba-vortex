## Sources

- [desc] Initial description: "Diseño de arquitectura completa de interoperabilidad para modernización del ecosistema Andes Retail: capa de integración entre sistemas legacy (ERP, WMS, OMS, CRM) y canales digitales (ecommerce, marketplaces, pasarela de pagos) sobre AWS EKS con enfoque Security-First PCI-DSS, Clean Architecture, resiliencia cloud-native y EDA"
- [scope] Workflow-selected scope: `feature`.
- [Q1] Dolor principal: incidentes recurrentes en picos de tráfico con pérdidas de venta directas.
- [Q2] Estado de APIs legacy: desconocido, se requiere discovery técnico en semanas 1-2.
- [Q3] Manejo de PAN: la pasarela ya tokeniza; el PAN nunca llega a sistemas de AndesRetail.
- [Q4] Criterio de éxito MVP: canal digital integrado end-to-end + eliminación de integraciones P2P + soporte 10x sin degradar legados.
- [Q5] Autoridad de decisión: compartida entre Vortexbird y el equipo técnico de AndesRetail.
- [Q6] Fuera del scope: multi-región, todos los marketplaces (piloto uno), certificación PCI formal.
- [Q7] Infraestructura: cuenta AWS existente, equipo con experiencia básica.
- [Q8] Multi-tenant: cada canal con credenciales, scopes y rate limits propios.

---

## Problem Statement

AndesRetail opera un ecosistema de sistemas de negocio maduros (ERP on-prem, WMS, OMS, CRM)
que hoy se comunican mediante integraciones punto a punto construidas de forma incremental
y sin un estándar común. [desc][Q1]

El resultado son tres categorías de riesgo activo en el negocio:

1. **Riesgo de disponibilidad:** En eventos promocionales, el volumen de tráfico
   (estimado en 10x el baseline) genera cascadas de fallos que impactan los sistemas
   core. Las pérdidas de venta por downtime durante CyberDay o Black Friday son el
   dolor más urgente. [Q1]

2. **Riesgo de integridad de datos:** Las integraciones P2P sin un modelo canónico
   producen inconsistencias de stock, precios y estado de órdenes entre el e-commerce
   propio, los marketplaces y los sistemas internos. Esto genera devoluciones, reclamos
   y erosión de confianza del cliente. [desc]

3. **Riesgo de seguridad y operación:** La ausencia de un plano de autenticación
   unificado y de trazabilidad end-to-end hace imposible auditar el acceso de
   canales externos y detectar anomalías a tiempo. [desc]

---

## Target Customer

**Operadores de canal externo (marketplaces, e-commerce):** consumen las APIs de
stock, precio y órdenes. Necesitan respuestas rápidas (< 300ms p95), contratos
estables y notificaciones de eventos en tiempo real. [Q8][desc]

**Equipo de operaciones de AndesRetail:** gestiona el catálogo, el inventario y
el fulfillment. Necesita que la capa de integración proteja los sistemas legacy
de sobrecargas y les dé visibilidad de lo que está ocurriendo. [Q4][desc]

**Equipo técnico de AndesRetail (co-owner):** participa en las decisiones de
arquitectura y será el responsable de operar la plataforma post-entrega. [Q5]

---

## Success Metrics

| Métrica | Objetivo | Fuente |
|---------|----------|--------|
| Latencia p95 para stock/precio | < 300ms | [desc] |
| Disponibilidad de APIs externas | 99.9% | [desc] |
| Capacidad de absorción de picos | 10x volumen sin degradar legados | [Q4] |
| Integraciones P2P eliminadas | Al menos las que involucran el canal piloto | [Q4] |
| Canal digital integrado en producción | 1 canal end-to-end en MVP | [Q4] |
| Trazabilidad de transacciones | 100% con correlation-id en logs | [desc] |

---

## Initiative Trigger

El detonante inmediato es la recurrencia de incidentes en picos de tráfico
con impacto directo en ingresos. [Q1]

El detonante estructural es la acumulación de deuda técnica en las integraciones:
cada nuevo canal o sistema requiere N integraciones nuevas, haciendo el costo
de mantenimiento insostenible a medida que el ecosistema de AndesRetail crece. [desc]

---

## Initial Scope Signal

**Workflow-selected scope:** `feature` [scope]

**Límite confirmado de esta entrega:** [Q6]
- Incluye: diseño e implementación de la capa de interoperabilidad, APIs estándar
  de stock/precio/órdenes/webhooks, integración con un canal piloto, infraestructura
  base AWS+EKS, seguridad OAuth2/OIDC multi-tenant, resiliencia y observabilidad.
- Excluye: arquitectura multi-región, onboarding de todos los marketplaces (se hace
  uno piloto), certificación PCI-DSS formal con QSA, refactoring de los sistemas
  legacy (ERP, WMS, OMS, CRM).

---

## Assumptions & Open Questions

- [assumption] La arquitectura de referencia usa AWS + EKS + Kong + Keycloak. Las herramientas son una propuesta, no están impuestas; se confirman en el discovery técnico. [desc]
- [assumption] Existe conectividad de red entre el datacenter on-premise y AWS. Si no existe, el costo y tiempo de provisión modifica el roadmap. [desc]
- [assumption] El ERP es la fuente de verdad para catálogo, precios e inventario contable. La capa de interoperabilidad no reemplaza ese rol. [desc]
- [assumption] El equipo de AndesRetail asigna representantes técnicos de cada sistema legacy para el discovery de APIs en semanas 1-2. Sin esto, el roadmap se extiende. [Q2][Q5]
- [assumption] La latencia p95 < 300ms aplica a consultas de stock/precio. El flujo completo de creación de orden (incluyendo autorización de pago) tiene un SLA más amplio (< 2s). [desc]
