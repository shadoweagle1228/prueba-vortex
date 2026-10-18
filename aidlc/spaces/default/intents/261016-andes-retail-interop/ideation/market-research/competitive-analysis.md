## Sources

- [desc] Initial description del proyecto.
- [MR-Q1] Integraciones actuales: código custom punto a punto, sin middleware previo.
- [MR-Q2] No existe análisis competitivo previo — este documento es el primer benchmark formal.
- [MR-Q5] Tendencia prioritaria: headless retail / separación frontend-backend via APIs.

---

## Competitive Analysis — Capa de Interoperabilidad Retail

### Contexto

AndesRetail no tiene hoy una capa de integración formal. [MR-Q1] Todas las integraciones
son código custom punto a punto, lo que coloca a la empresa en una posición de
**deuda técnica activa** frente a competidores que ya adoptaron arquitecturas
orientadas a APIs y eventos. [MR-Q2]

Esta sección analiza cómo empresas comparables en el sector retail latinoamericano
y global resolvieron el mismo problema, y qué patrones emergieron como estándar de facto.

---

### Referentes del Sector

#### Falabella — API-First Omnicanal
Falabella construyó una plataforma de integración interna que expone un API Gateway
centralizado hacia todos sus canales (Falabella.com, Linio, Sodimac, Tottus). La
capa maneja la traducción de modelos de datos entre sus ERPs por país y los canales
digitales. El enfoque es **API-first con event backbone** (Kafka) para propagación
de estado de órdenes e inventario. [MR-Q2]

**Lección aplicable a AndesRetail:** La separación entre el plano de APIs externas
(contratos estables, versionados) y el plano interno de integración con legados
es la decisión que más reduce el costo de mantenimiento a largo plazo. [MR-Q5]

#### Mercado Libre — Plataforma de Marketplace con Multi-Tenant
Mercado Libre expone APIs públicas para sellers (stock, precios, órdenes) con modelo
multi-tenant estricto: cada seller tiene su propio rate limit, scopes OAuth2 y contrato
de webhook. El backend de integración usa adaptadores por cada sistema de fulfillment
del seller.

**Lección aplicable:** El modelo multi-tenant por canal (confirmado en [Q8] del intent)
es exactamente el patrón que usan los marketplaces líderes. La inversión en
implementarlo bien desde el inicio paga dividendos cuando se onboardean nuevos canales. [MR-Q5]

#### VTEX — Plataforma Composable Commerce
VTEX es el referente de **composable commerce** en LATAM. Su arquitectura separa
el catálogo, el pricing, el checkout y el OMS en APIs independientes, cada una
con su propio ciclo de vida. Permite reemplazar componentes sin reescribir todo.

**Lección aplicable:** El patrón de Bounded Contexts por dominio (catálogo, inventario,
órdenes, pagos) que se propone para AndesRetail es consistente con la dirección
que tomó el mercado. No es una apuesta arriesgada — es el estándar emergente. [MR-Q5]

---

### Patrones Competitivos Identificados

| Patrón | Adoptado por | Relevancia para AndesRetail |
|--------|-------------|----------------------------|
| API Gateway centralizado con rate limiting por canal | Falabella, ML, VTEX | Alta — [Q8] multi-tenant confirmado |
| Event backbone para propagación de estado (Kafka/EventBridge) | Falabella, Amazon | Alta — elimina polling y reduce carga en legados |
| Anti-Corruption Layer hacia sistemas legacy | Falabella, SAP Commerce | Alta — [MR-Q1] integraciones custom actuales |
| Schema Registry para contratos de eventos | Confluent (Kafka), AWS | Media — reduce errores de integración en el tiempo |
| Cache de catálogo/stock con TTL corto | Todos los retailers a escala | Crítica — [Q4] intent: p95 < 300ms |
| Multi-tenant OAuth2 con scopes por canal | Mercado Libre, Shopify | Alta — [Q8] del intent |

---

### Posición Competitiva de AndesRetail (Hoy vs. Post-Implementación)

| Dimensión | Hoy | Post-MVP |
|-----------|-----|----------|
| Integración de nuevos canales | 3-6 meses de desarrollo custom | 2-4 semanas (onboarding estándar) |
| Resiliencia en picos | Degradación en cascada | Absorción 10x con cache y circuit breakers |
| Trazabilidad | Nula (no hay correlation-id) | 100% de transacciones trazadas |
| Time-to-market para nueva API | Alto (acoplamiento con legados) | Bajo (adaptadores aislados) |
| Posición frente a Falabella/Ripley | Rezagada | Comparable en la capa de integración |

---

## Assumptions & Open Questions

- [assumption] Los datos de Falabella y Mercado Libre provienen de conocimiento público del sector (conferencias técnicas, blogs de ingeniería). No son resultado de investigación primaria con esas empresas.
- [assumption] Se asume que AndesRetail compite en el segmento mediano del retail chileno/latinoamericano, donde los referentes mencionados representan el estado del arte hacia el que converger, no el punto de partida actual.
