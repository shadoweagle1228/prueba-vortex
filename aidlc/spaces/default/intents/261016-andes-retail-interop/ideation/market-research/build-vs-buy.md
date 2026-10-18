## Sources

- [desc] Initial description del proyecto: AWS + EKS + Kong + Keycloak como referencia no impuesta.
- [MR-Q3] API Gateway: decisión abierta, necesitamos evaluación.
- [MR-Q4] IdP OAuth2/OIDC: abierto, con costo como restricción de primer orden.
- [Q7] Del intent: cuenta AWS existente, equipo con experiencia básica en AWS.
- [MR-Q1] No hay middleware previo — partimos desde cero en la capa de integración.

---

## Build vs. Buy Assessment

### Criterios de Evaluación

Dado que el costo es una restricción explícita [MR-Q4], los criterios se ponderan así:

| Criterio | Peso |
|----------|------|
| Costo total de propiedad (TCO 3 años) | 30% |
| Complejidad operacional para el equipo actual | 25% |
| Fit con AWS (VPC, IAM, CloudWatch) | 20% |
| Evitación de vendor lock-in | 15% |
| Tiempo de adopción (curva de aprendizaje) | 10% |

---

### Decisión 1: API Gateway [MR-Q3]

#### Opciones evaluadas

**Opción A — Kong (self-hosted en EKS)**
- Costo: open source (Kong Gateway OSS), sin licencia. Costo operacional: ~0.5 FTE/mes para operar el cluster y los plugins.
- Fit AWS: requiere configuración manual para integrar con CloudWatch, ALB, Secrets Manager. No es nativo AWS.
- Vendor lock-in: bajo — Kong es open source, los plugins son portables.
- Curva de aprendizaje: media. El equipo necesita conocer el modelo de plugins y el ciclo de vida de los Ingress en EKS.
- **Ventaja clave:** Control total sobre la configuración, sin costo de licencia a escala, amplio ecosistema de plugins (JWT, rate limiting, OAuth2, CORS, logging).

**Opción B — AWS API Gateway (REST/HTTP)**
- Costo: $3.50/millón de requests + $0.09/GB de datos. A volumen de 50M req/mes → ~$175/mes solo en API calls. Sin costo operacional de infraestructura.
- Fit AWS: nativo. Integración directa con IAM, CloudWatch, WAF, Lambda, VPC Link.
- Vendor lock-in: alto — los contratos de API, las políticas de throttling y los modelos de autorización están en formato propietario de AWS.
- Curva de aprendizaje: baja para equipos con experiencia AWS básica [Q7].
- **Ventaja clave:** Zero-ops. No hay nodos que gestionar, parchear ni escalar.

**Opción C — Traefik (self-hosted en EKS)**
- Costo: open source. Costo operacional similar a Kong.
- Fit AWS: menor que Kong. Menos plugins nativos para JWT/OAuth2.
- **Desventaja:** Ecosistema más pequeño para casos de uso enterprise retail.

#### Recomendación: Arquitectura híbrida AWS API Gateway + Kong

Para el MVP, usar **AWS API Gateway** como punto de entrada público (cero-ops,
integrado con WAF, Route 53, ACM) y **Kong** como API Gateway interno en EKS
para enrutamiento, validación JWT avanzada y rate limiting por tenant. [MR-Q3]

Esta combinación aprovecha lo mejor de ambos:
- AWS API Gateway absorbe el tráfico externo sin gestión de infraestructura.
- Kong maneja la lógica de negocio del gateway (multi-tenant, plugins custom) dentro del clúster.
- El costo escala con el uso en el edge (AWS API GW) y es fijo en el interior (Kong OSS).

**Revisión en mes 3:** si el volumen de requests supera los 200M/mes, evaluar
migrar el edge a Kong + NLB para reducir el costo variable de AWS API Gateway.

---

### Decisión 2: IdP OAuth2/OIDC [MR-Q4]

#### Opciones evaluadas

**Opción A — Keycloak (self-hosted en EKS)**
- Costo: open source, sin licencia. Costo operacional: RDS Aurora para persistencia (~$50-80/mes en dev, ~$200/mes en prod) + recursos EKS.
- Fit AWS: requiere integración manual con Secrets Manager para client secrets. No es nativo.
- Vendor lock-in: bajo — open source, estándar OIDC.
- Complejidad operacional: **alta**. Keycloak requiere gestión de cluster, backups de RDS, configuración de realms, rotación de llaves JWKS. Para un equipo con experiencia básica en AWS [Q7], esto es una carga operacional significativa.
- **Ventaja clave:** Control total, cero costo de licencia, soporte completo de OIDC/OAuth2/SAML/WebAuthn.

**Opción B — AWS Cognito**
- Costo: gratuito para los primeros 50,000 MAU. $0.0055/MAU adicional. Para 100K usuarios → ~$275/mes. Para B2B (canales externos como tenants), el costo por MAU puede ser manejable.
- Fit AWS: nativo. Integración directa con ALB, API Gateway, IAM, CloudWatch.
- Vendor lock-in: alto — los flows de Cognito (User Pools, Identity Pools) son propietarios de AWS.
- Complejidad operacional: **baja**. Zero-ops en el IdP mismo.
- **Limitación clave:** Customización limitada de flows de autenticación. La UI de login es difícil de personalizar. Las políticas de tokens tienen menos flexibilidad que Keycloak. Para multi-tenant B2B con scopes custom por canal, puede quedarse corto.

**Opción C — Auth0 / Okta**
- Costo: Auth0 Developer tiene tier gratuito hasta 7,500 MAU. El plan B2B empieza en ~$800/mes. Para el modelo multi-tenant de AndesRetail, el costo puede escalar rápidamente. **No recomendado dado la restricción de costo [MR-Q4].**
- Vendor lock-in: muy alto.

#### Recomendación: Keycloak con playbook operacional simplificado

**Keycloak** es la opción correcta para AndesRetail dado el modelo multi-tenant
complejo (scopes custom por canal, ABAC por tenant, flujos OAuth2 avanzados). [MR-Q4]

Para mitigar la complejidad operacional (riesgo del equipo con experiencia básica [Q7]):
- Desplegar Keycloak en EKS con un Helm chart mantenido (Bitnami Keycloak).
- Persistencia en RDS Aurora Serverless v2 (escala a cero en dev, reduce costo).
- Configuración de Keycloak como IaC (Terraform/CDK + Keycloak Terraform provider).
- Runbook documentado para operaciones de día 2 (rotación de llaves, backup de realm, reset de client secrets).

**Revisión en mes 6:** si el equipo operacional de AndesRetail no logra absorber la
gestión de Keycloak, migrar a AWS Cognito para los casos de uso simples y mantener
Keycloak solo para los tenants con requerimientos avanzados.

---

### Decisión 3: Bus de Eventos [desc]

#### Opciones evaluadas

| Opción | TCO | Ops | Fit AWS | Lock-in |
|--------|-----|-----|---------|---------|
| Amazon EventBridge | Bajo ($1/millón de eventos custom) | Zero | Nativo | Alto |
| Apache Kafka (MSK) | Medio ($200-400/mes MSK) | Medio | Nativo (MSK) | Bajo |
| RabbitMQ (self-hosted) | Bajo (open source) | Alto | No nativo | Bajo |

#### Recomendación: Amazon EventBridge

Para el volumen de eventos esperado en AndesRetail (órdenes, actualizaciones de
stock, eventos de pago), **EventBridge** es la opción correcta. El costo es mínimo
a los volúmenes actuales, la operación es zero-ops, y la integración con SQS, Lambda
y EKS es nativa. [desc][Q7]

Kafka (MSK) se justifica cuando se necesita procesamiento de streams de alta
frecuencia (>10K eventos/segundo sostenidos) o retención de eventos para replay.
AndesRetail no está en ese escenario en el MVP. Puede ser una evolución futura. [assumption]

---

## Assumptions & Open Questions

- [assumption] El análisis de costos usa precios de AWS publicados (octubre 2026) y puede variar por región (sa-east-1 tiene precios ~15% más altos que us-east-1 en algunos servicios). Los valores son orientativos para la decisión, no cotizaciones firmes.
- [assumption] La recomendación de Keycloak asume que el equipo de AndesRetail puede dedicar al menos 0.25 FTE a la gestión operacional del IdP. Si no es posible, AWS Cognito es la alternativa.
- [assumption] El volumen de eventos de EventBridge se estima en < 5M eventos/mes en el MVP, lo que resulta en un costo < $5/mes — irrelevante en el TCO total.
