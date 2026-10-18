## Sources

- [desc] Initial description del proyecto.
- [F-Q1] Regulación Colombia: Ley 1581/2012 + Decreto 1377/2013 + enmascaramiento PII.
- [F-Q2] Equipo Vortexbird construye todo.
- [F-Q3] Restricción de costo: optimizar al máximo.
- [F-Q4] Sin bloqueos organizacionales identificados.
- [F-Q5] Estado de conectividad on-premise desconocido — dependencia crítica.
- [MR-Q1] Sin middleware previo — punto de partida desde cero.
- [MR-Q3] API Gateway: arquitectura híbrida AWS API GW + Kong recomendada.
- [MR-Q4] IdP: Keycloak recomendado con playbook operacional.

---

## Feasibility Assessment

### Veredicto General: **VIABLE con condiciones**

El proyecto es técnicamente viable en el scope y timeframe propuestos (MVP 4-6 semanas),
sujeto a la resolución de tres condiciones previas identificadas. Sin estas condiciones,
el roadmap se extiende y algunos criterios de éxito del intent no pueden garantizarse.

---

### Viabilidad Técnica

#### ✅ Alta viabilidad — Stack tecnológico

El stack propuesto (AWS EKS + Kong + Keycloak + EventBridge + Redis) es maduro,
ampliamente adoptado en proyectos comparables y cuenta con soporte activo de la
comunidad. El equipo de Vortexbird puede construirlo con las habilidades estándar
de un equipo cloud-native. [F-Q2][desc]

La decisión de Keycloak sobre Auth0/Okta es correcta dado la restricción de costo [F-Q3]:
el costo de licencia de Auth0 para el modelo multi-tenant de AndesRetail sería de
$800-2000/mes vs. $0 con Keycloak. La complejidad operacional adicional está
documentada y mitigada en el build-vs-buy. [MR-Q4]

La arquitectura híbrida AWS API GW + Kong optimiza el costo: AWS API GW tiene costo
variable por request (escala a cero en no-uso) y Kong OSS no tiene licencia. [MR-Q3][F-Q3]

#### ⚠️ Viabilidad condicionada — Conectividad on-premise

El Stock Adapter requiere conectividad de baja latencia (< 250ms RTT) hacia el ERP
on-premise. Sin Direct Connect o VPN Site-to-Site con QoS garantizada, el SLA de
p95 < 300ms para stock/precio no puede cumplirse. [F-Q5][desc]

**Condición 1:** Resolver el estado de conectividad on-premise en la semana 1.
Si no existe conectividad dedicada, el tiempo de provisionamiento de Direct Connect
(4-12 semanas) desplaza el roadmap del MVP significativamente.

**Alternativa si no hay conectividad:** Diseñar el Stock Adapter con un modo
"cache-first exclusivo" donde el stock se sincroniza en batch desde el ERP cada
5-15 minutos y todas las consultas se responden desde Redis. Esto sacrifica la
frescura del dato de stock pero mantiene el SLA de latencia. [assumption]

#### ⚠️ Viabilidad condicionada — APIs del ERP

El estado de las APIs del ERP on-premise es desconocido [MR-Q1]. Si el ERP solo
expone interfaces batch (FTP, IDoc, RFC), el tiempo de construcción del ACL se
extiende 2-3 semanas adicionales.

**Condición 2:** Sesión de discovery técnico con el equipo del ERP en la semana 1
para confirmar qué interfaces están disponibles hoy.

#### ✅ Alta viabilidad — Regulación Colombia

La Ley 1581/2012 de Colombia (Habeas Data) es compatible con la arquitectura
propuesta. Los requerimientos principales que impactan el diseño son: [F-Q1]

- **Enmascaramiento de PII en logs:** los campos de datos personales (nombre, email,
  teléfono, dirección) deben estar enmascarados en los logs de aplicación. Esto se
  implementa con un middleware de log sanitization en cada servicio.
- **Finalidad del tratamiento:** los datos personales solo se usan para el propósito
  declarado (procesar la orden). El Order Service no puede reutilizar datos de cliente
  para otros fines sin consentimiento.
- **Registro ante la SIC:** AndesRetail debe registrar sus bases de datos de datos
  personales ante la Superintendencia de Industria y Comercio. Esto es responsabilidad
  de AndesRetail, no del equipo de Vortexbird, pero el diseño debe documentar qué
  datos personales maneja cada servicio para facilitar ese registro. [assumption]
- **Seguridad del dato:** cifrado en tránsito (TLS 1.3) y en reposo (KMS) — ya
  contemplado en el diseño base. [desc]

PCI-DSS aplica por el manejo de pagos. La arquitectura de tokenización (el PAN nunca
llega a los sistemas de AndesRetail) minimiza el scope PCI al máximo posible. [F-Q1][Q3 intent]

---

### Viabilidad de Equipo y Tiempo

El equipo de Vortexbird construye todo. [F-Q2] Para cumplir el MVP en 4-6 semanas
se requiere un equipo mínimo de:

| Rol | Dedicación |
|-----|-----------|
| Arquitecto/a Senior (diseño + IaC) | 100% |
| Backend Developer (Kotlin + Spring Boot) | 100% |
| DevOps/Platform Engineer (EKS + CI/CD) | 100% |
| QA / Testing | 50% (desde semana 3) |

Con un equipo menor o con dedicación parcial, el MVP se extiende a 8-10 semanas.

---

### Restricción de Costo — Estimación Mensual MVP (sa-east-1)

| Componente | Costo mensual estimado |
|-----------|----------------------|
| EKS Cluster (control plane) | $72 |
| EC2 Nodos (2x m6g.medium) | ~$60 |
| RDS Aurora Serverless v2 (dev) | ~$30-50 |
| ElastiCache Redis (t4g.small) | ~$25 |
| AWS API Gateway (10M req/mes) | ~$35 |
| EventBridge (5M eventos) | ~$5 |
| Secrets Manager (5 secretos) | ~$2 |
| CloudWatch Logs + Metrics | ~$20 |
| NAT Gateway (50GB) | ~$25 |
| **Total estimado MVP** | **~$274-294/mes** |

En producción (con instancias más grandes y multi-AZ): ~$600-900/mes. [F-Q3][assumption]

---

## Assumptions & Open Questions

- [assumption] La estimación de costo usa precios de AWS sa-east-1 (São Paulo). Si el deployment es en us-east-1 (N. Virginia), los costos son ~10-15% menores.
- [assumption] La alternativa "cache-first exclusivo" para el Stock Adapter (en caso de no tener conectividad on-premise) sacrifica la consistencia fuerte del stock por consistencia eventual. Para el contexto de retail colombiano, esto es aceptable con un TTL de 15 minutos y un proceso de reconciliación diario.
- [assumption] El registro ante la SIC (Colombia) de las bases de datos de datos personales es responsabilidad de AndesRetail. Vortexbird provee la documentación de qué datos maneja cada servicio para facilitar ese proceso.
