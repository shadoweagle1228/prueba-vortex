## Sources

- [desc] Initial description: "Diseño de arquitectura completa de interoperabilidad para modernización del ecosistema Andes Retail: capa de integración entre sistemas legacy (ERP, WMS, OMS, CRM) y canales digitales (ecommerce, marketplaces, pasarela de pagos) sobre AWS EKS con enfoque Security-First PCI-DSS, Clean Architecture, resiliencia cloud-native y EDA"
- [scope] Workflow-selected scope: `feature`.
- [Q1] Del intent: integraciones punto a punto sin middleware; dolor principal en picos de tráfico.
- [Q4] Del intent: modelo multi-tenant por canal.
- [Q7] Del intent: cuenta AWS existente, equipo con experiencia básica.

---

## MR-Q1. ¿AndesRetail ya intentó resolver este problema antes?

[Answer]: A. No hay nada — las integraciones son código custom punto a punto

---

## MR-Q2. ¿Hay referentes o benchmarks competitivos conocidos?

[Answer]: D. No se ha hecho ese análisis competitivo todavía

---

## MR-Q3. ¿Hay decisión tomada sobre el API Gateway?

[Answer]: C. Está completamente abierto — necesitamos la evaluación

---

## MR-Q4. ¿Hay decisión tomada sobre el IdP OIDC/OAuth2?

Nota adicional: el costo es un factor de decisión clave.

[Answer]: D. Está abierto — necesitamos la evaluación, con costo como restricción

---

## MR-Q5. ¿Qué tendencia de mercado tiene más impacto?

[Answer]: C. Headless retail — separar el frontend de la lógica de negocio via APIs

---

## Assumptions & Open Questions

- [assumption] La ausencia de análisis competitivo previo (MR-Q2: D) implica que este documento es el primer ejercicio formal de benchmarking para AndesRetail. Las referencias usadas son de conocimiento del sector, no de investigación primaria con el cliente.
- [assumption] El costo de licenciamiento/operación es un criterio de primer orden en la evaluación build-vs-buy para el IdP (MR-Q4). Soluciones con costo por MAU (Monthly Active User) como Auth0/Okta pueden resultar prohibitivas a escala.
