## Sources

- [desc] Initial description del proyecto.
- [Q1] Del intent: dolor principal en picos de tráfico; cuenta AWS existente con equipo básico.
- [MR-Q1] No hay middleware previo — integraciones código custom punto a punto.
- [MR-Q4] IdP abierto con costo como restricción de primer orden.

---

## F-Q1. ¿Cuáles regulaciones aplican a AndesRetail?

Nota: AndesRetail opera en Colombia, no en Chile.

[Answer]: Ley de Protección de Datos Personales de Colombia (Ley 1581/2012 + Decreto 1377/2013) + enmascaramiento de PII. PCI-DSS aplica por el manejo de pagos.

---

## F-Q2. ¿El equipo que construye ya existe?

[Answer]: Equipo Vortexbird construye todo el proyecto.

---

## F-Q3. ¿Hay techo de presupuesto de infraestructura AWS?

[Answer]: Sin techo definido — optimizar costo al máximo posible como restricción de diseño.

---

## F-Q4. ¿Hay bloqueos organizacionales conocidos?

[Answer]: No se tienen identificados aún.

---

## F-Q5. ¿Existe conectividad dedicada on-premise → AWS?

[Answer]: D — No se conoce el estado actual. Es una dependencia a resolver en el discovery técnico.

---

## Assumptions & Open Questions

- [assumption] La regulación aplicable es la Ley 1581/2012 de Colombia (Habeas Data) y su decreto reglamentario 1377/2013. Esto implica obligaciones de: finalidad del tratamiento de datos, consentimiento del titular, registro nacional de bases de datos (RNBD) ante la SIC, y procedimientos de atención de derechos ARCO.
- [assumption] La conectividad on-premise → AWS (F-Q5: D) es una dependencia de primer orden. Si no existe Direct Connect o VPN Site-to-Site, el Stock Adapter no puede consultar el ERP on-premise con la latencia requerida (< 250ms). Esto debe resolverse en la semana 1 del proyecto.
- [assumption] La restricción de costo (F-Q3) se traduce en preferencia por servicios serverless/managed sobre self-hosted donde el delta de costo lo justifique, y uso de instancias ARM (Graviton) en EKS por su mejor relación precio/rendimiento.
