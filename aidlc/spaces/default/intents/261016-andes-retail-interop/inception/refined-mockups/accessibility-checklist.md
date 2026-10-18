## Sources

- [desc] Proyecto API/backend sin UI de usuario final.
- [CA-01] PCI-DSS — acceso y auditoría de la plataforma.

---

## Accessibility Checklist — API Platform

Este proyecto no tiene interfaz de usuario para el usuario final, por lo que
no aplican los requerimientos de accesibilidad WCAG (diseñados para interfaces web).

Sin embargo, los siguientes controles de **accesibilidad operacional** aplican
para los operadores de plataforma (P-02) que interactúan con la PIE:

| Control | Requisito | Estado |
|---------|-----------|--------|
| Documentación de API | OpenAPI Spec publicada en Developer Portal — legible por herramientas de screen reader y parseable por cualquier cliente | Pendiente (Contract Design stage) |
| Mensajes de error | Mensajes en español, claros y accionables — sin códigos internos crípticos expuestos al consumidor | Implementado en interaction-spec.md |
| Runbooks operacionales | Documentación paso a paso sin jerga innecesaria — operador puede seguirlos sin conocimiento profundo del código | Pendiente (NFR Design stage) |
| Logs estructurados | Formato JSON legible por Grafana, OpenSearch y cualquier herramienta de observabilidad estándar | Estándar definido en design-system-mapping.md |
| Alarmas accionables | Cada alarma tiene un mensaje en lenguaje natural con la acción a tomar — no solo un código de error | Pendiente (NFR Design stage) |

> Nota: Si en el futuro se agrega un portal de administración web para operadores, se requeriría una evaluación WCAG 2.1 AA completa, incluyendo testing manual con tecnologías asistivas. La validación automática no es suficiente para cumplimiento WCAG.

---

## Assumptions & Open Questions

- [assumption] La plataforma no tiene una interfaz de administración web en el scope del MVP. Si se agrega en el mes 6, se debe incorporar una evaluación de accesibilidad al alcance de ese sprint.
