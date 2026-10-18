## Sources

- [desc] Initial description del proyecto.
- [MR-Q5] Tendencia prioritaria confirmada: headless retail / separación frontend-backend via APIs.
- [Q4] Del intent: criterio de éxito incluye soporte a picos 10x.
- [Q8] Del intent: modelo multi-tenant por canal.

---

## Market Trends — Integración y Retail Digital

### Tendencia 1 (Prioritaria): Headless Retail y API-First [MR-Q5]

El mercado retail se mueve hacia la separación entre la capa de presentación
(frontend web/móvil) y la lógica de negocio (catálogo, precios, inventario, órdenes).
Esta separación se materializa mediante APIs — cada dominio expone su propia API,
y los frontends consumen solo lo que necesitan.

**Impacto directo en AndesRetail:** La capa de interoperabilidad que se va a construir
**es** la habilitación de headless retail. Hoy el e-commerce de AndesRetail está
acoplado directamente a los sistemas legacy. Con la nueva capa, el frontend puede
evolucionar independientemente. Cualquier canal nuevo (app móvil, kiosco en tienda,
chatbot) consume las mismas APIs sin necesidad de integraciones adicionales. [MR-Q5][desc]

**Estándar emergente:** MACH Architecture (Microservices, API-first, Cloud-native,
Headless) es el marco que los retailers medianos y grandes están adoptando como
objetivo de arquitectura en el periodo 2024-2027.

---

### Tendencia 2: Real-Time Inventory (contexto de los marketplaces)

Los marketplaces líderes (Mercado Libre, Falabella.com) exigen actualización
de stock en tiempo cuasi-real (< 5 minutos) como condición para mantener la
visibilidad de los productos del seller. Los retailers que no pueden garantizar
esto pierden posicionamiento algorítmico. [Q4][desc]

**Impacto:** El cache de stock con TTL corto (60s propuesto) y la propagación de
eventos de actualización de inventario via EventBridge no son solo decisiones técnicas
de resiliencia — son requisitos de negocio para competir en marketplaces. [MR-Q5]

---

### Tendencia 3: Event-Driven Architecture en Retail

Los principales sistemas de e-commerce (Shopify, VTEX, BigCommerce) migran hacia
arquitecturas orientadas a eventos para la propagación de estado de órdenes,
inventario y precios. Los webhooks salientes (orden confirmada, pago aprobado,
despacho iniciado) son hoy un requisito de integración de los marketplaces — no un
diferenciador. [desc]

**Impacto:** El diseño de webhooks/eventos en el brief responde directamente a
esta tendencia. No implementarlos es quedar fuera del ecosistema de marketplaces
modernos. [Q8]

---

### Tendencia 4: Multi-Tenant API Platforms y Partner Ecosystems

Los retailers que crecen via marketplaces necesitan tratar a cada canal externo como
un tenant con su propio ciclo de vida: onboarding, scopes de acceso, rate limits,
SLAs documentados y mecanismos de deprecación de versiones. La tendencia es hacia
plataformas de API con portales de desarrollador. [Q8][desc]

**Impacto:** El modelo multi-tenant por canal (confirmado en intent) es una
inversión que paga dividendos cada vez que se agrega un nuevo marketplace o partner.
El costo de implementarlo mal al inicio es la acumulación de excepciones y casos
especiales que degradan la arquitectura. [Q8]

---

### Síntesis de Implicaciones para el Diseño

| Tendencia | Decisión de diseño que habilita |
|-----------|-------------------------------|
| Headless retail | APIs por dominio con contratos versionados y estables |
| Real-time inventory | Cache Redis con TTL corto + eventos de invalidación |
| Event-Driven Architecture | EventBridge como bus de eventos + webhooks salientes |
| Multi-tenant platforms | OAuth2 con scopes por canal + rate limiting por tenant |

Ninguna de estas tendencias es especulativa para el sector — son el estado actual
del mercado en el segmento de retail digital latinoamericano. La arquitectura propuesta
converge con todas ellas simultáneamente. [MR-Q5][desc]

---

## Assumptions & Open Questions

- [assumption] El análisis de tendencias se basa en conocimiento del sector retail tech (2023-2026). No se realizó investigación primaria (encuestas, entrevistas con analistas). Las tendencias representan el consenso del mercado, no predicciones especulativas.
- [assumption] La adopción de MACH Architecture como marco de referencia es una hipótesis de posicionamiento, no un mandato. AndesRetail puede adoptar los principios individualmente sin comprometerse con el marco completo.
