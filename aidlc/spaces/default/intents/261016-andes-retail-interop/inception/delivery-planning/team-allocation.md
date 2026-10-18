## Sources

- [desc] Initial description del proyecto Andes Retail.
- [mob-composition] Modelo de trabajo y composición de equipo de team-formation.
- [F-Q2] Equipo Vortexbird construye todo el proyecto.
- [U1] a [U8] del units.md de units-generation.
- [bolt-plan.md] Plan de entregas por Bolts.

---

# Asignación de Equipos y Responsabilidades — Andes Retail PIE

Este documento establece la distribución de trabajo entre los miembros del equipo Vortexbird para cada **Bolt** (un ciclo de construcción sobre una porción del trabajo que culmina en algo ejecutable y verificable). Actúa como el análogo al **Program Board** (un tablero de coordinación visual que mapea entregables entre múltiples roles y sus dependencias a lo largo del tiempo).

---

## Modelo Operativo y Equipos de Trabajo

El equipo técnico de Vortexbird opera bajo un esquema de **mob** (un grupo colaborativo de desarrollo enfocado conjuntamente en un objetivo) adaptado a modalidad de *pairing* (parejas especializadas) para maximizar la calidad del código, el cumplimiento de Clean Architecture y la velocidad de entrega sin incurrir en overhead burocrático.

### Composición de Roles

| Rol | Especialista Principal | Foco Primario |
|---|---|---|
| **Arquitecto de Soluciones / Tech Lead** | Arquitecto Cloud Vortexbird | Clean Architecture, PCI-DSS, diseño de Sagas, resiliencia y contratos. |
| **Backend Developer Senior** | Ingeniero Backend Kotlin | Implementación de dominio, TDD, adaptadores, Outbox pattern y Resilience4j. |
| **DevOps / Platform Engineer** | Ingeniero Cloud & IaC | CDK Python, EKS, redes privadas, VPC endpoints, Keycloak, CI/CD y observabilidad. |
| **QA / Performance Specialist** | Ingeniero de Calidad | Suites de pruebas en k6, pruebas de seguridad estática y automatización de pruebas. |

---

## Asignación por Bolt (Program Board)

| Bolt | Nombre del Bolt | Unidad | Modalidad de Trabajo | Pareja / Asignación Principal | Revisión y Aprobación |
|---|---|---|---|---|---|
| **Bolt 1** | `walking-skeleton` | `skeleton-stock-query` (U1) | Pairing Técnico | Backend Dev + DevOps Engineer | Arquitecto de Soluciones |
| **Bolt 2** | `catalog-price` | `price-query` (U2) | Individual + Pair | Backend Dev (soporte DevOps en Redis) | Arquitecto de Soluciones |
| **Bolt 3** | `orders-core` | `order-lifecycle` (U3) | Pairing Técnico | Backend Dev + Arquitecto de Soluciones | Tech Lead AndesRetail |
| **Bolt 4** | `payment-and-saga` | `payment-authorization` (U4) | Pairing Técnico | Backend Dev + Arquitecto de Soluciones | Security Lead AndesRetail |
| **Bolt 5** | `notification-webhooks` | `notification-webhooks` (U5) | Pairing Técnico | Backend Dev + DevOps Engineer | Arquitecto de Soluciones |
| **Bolt 6** | `security-hardening` | `security-compliance` (U6) | Pairing Técnico | DevOps Engineer + Arquitecto de Soluciones | Tech Lead AndesRetail |
| **Bolt 7** | `observability-and-ops` | `observability-baseline` (U7) | Individual + Pair | DevOps Engineer + QA Specialist | Arquitecto de Soluciones |
| **Bolt 8** | `load-and-sla-validation` | `load-validation` (U8) | Colaborativo (Mob) | QA Specialist + Backend Dev + DevOps | Whole Team & PO AndesRetail |

---

## Puntos de Sincronización y Puertas de Calidad (Quality Gates)

1. **Gate Post-Bolt 1 (Walking Skeleton Demo):** Revisión obligatoria con el equipo técnico de AndesRetail demostrando la conectividad end-to-end antes de iniciar la lógica transaccional de órdenes.
2. **Gate Post-Bolt 4 (Saga y Pagos CDE):** Revisión conjunta de seguridad PCI-DSS y validación del mecanismo de compensación de inventario con los líderes técnicos.
3. **Gate Post-Bolt 8 (Pase a Operación):** Presentación del reporte consolidado de pruebas de carga k6 certificando latencia p95 < 300ms y soporte de picos 10x ante el Product Owner.

---

## Assumptions & Open Questions

- [assumption] Toda la fase de Construcción dentro del flujo de trabajo AI-DLC se orquesta de forma secuencial y unificada en la sesión activa (`solo execution`), invocando los estándares definidos en esta matriz.
- [assumption] Cada PR generado en los Bolts requiere revisión técnica de Clean Architecture y verificación automatizada de tests verdes en CI antes del merge a `main`.
