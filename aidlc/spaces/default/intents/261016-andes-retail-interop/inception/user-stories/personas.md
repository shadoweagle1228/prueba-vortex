## Sources

- [desc] Initial description del proyecto.
- [Q8] Del intent: modelo multi-tenant por canal.
- [scope] Scope document — actores identificados.

---

## Personas

### P-01 — Operador de Canal Externo (Sistema)
**Tipo:** Sistema automatizado (marketplace o e-commerce)
**Representa:** El sistema técnico de un canal externo que consume las APIs de la PIE para consultar stock/precios y crear órdenes en nombre de sus usuarios.
**Necesidades:** APIs estables con SLA documentado, autenticación OAuth2 con credenciales propias, webhooks confiables para notificaciones de eventos, rate limits predecibles.
**Contexto:** Opera con token `client_credentials`. Tiene su propio `tenantId` y `scopes`. Sus llamadas no son interactivas — son procesos automatizados.
**Trazabilidad:** [Q8 intent], [FR-11], [desc]

---

### P-02 — Operador de Plataforma (AndesRetail/Vortexbird)
**Tipo:** Persona técnica
**Representa:** El ingeniero de plataforma que configura y opera la PIE: onboarda nuevos canales, monitorea el estado de los Circuit Breakers, responde a alertas de DLQ y gestiona secretos.
**Necesidades:** Runbooks claros, alertas accionables, capacidad de onboardear un canal nuevo sin cambios de código, visibilidad del estado del sistema en Grafana.
**Contexto:** Accede al cluster EKS y a las consolas de AWS. No es el usuario final de las APIs.
**Trazabilidad:** [scope] Flujo D, [FR-12], [NFR-04]

---

### P-03 — Sistema ERP (Legacy)
**Tipo:** Sistema legacy on-premise
**Representa:** El ERP de AndesRetail (fuente de verdad de stock y precios). Es un consumidor pasivo — recibe requests del Stock Adapter, no inicia llamadas.
**Necesidades:** No recibir más carga de la que puede manejar. Que el Circuit Breaker y el Bulkhead lo protejan de picos de tráfico.
**Trazabilidad:** [CA-02], [FR-01], [FR-06]

---

## Assumptions & Open Questions

- [assumption] No hay un "usuario final" (persona humana) que interactúa directamente con la PIE. Toda interacción es sistema-a-sistema. Las personas que SÍ podrían tener historias en el futuro son los usuarios finales del e-commerce, pero eso es responsabilidad del frontend de AndesRetail — fuera del scope de la PIE.
