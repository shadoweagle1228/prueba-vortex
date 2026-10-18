## Sources

- [F-Q2] Equipo Vortexbird construye todo.
- [CA-01] PCI-DSS — seguridad como capacidad obligatoria.
- [CA-02] Backpressure — Resilience4j como capacidad técnica requerida.
- [CA-03] Clean Architecture — capacidad técnica obligatoria.
- [MR-Q3] Kong como API Gateway interno.
- [MR-Q4] Keycloak como IdP.
- [TF-stack] Stack confirmado: Kotlin + Spring Boot (servicios backend) / Python + AWS CDK (IaC).

---

## Skill Matrix

### Capacidades Requeridas por Bloque del Backlog

| Capacidad | Bloque | Nivel requerido | Rol responsable |
|-----------|--------|----------------|----------------|
| **Python + AWS CDK** | F-01, F-02, F-03 | Senior | Arquitecto / DevOps |
| AWS EKS (Kubernetes) | F-02, S-03 | Senior | DevOps / Platform |
| AWS Secrets Manager + ESO | F-03 | Medio | DevOps / Platform |
| Keycloak OIDC/OAuth2 | S-01 | Senior | Arquitecto / DevOps |
| AWS API Gateway + WAF | S-02 | Medio | DevOps / Platform |
| Kong Ingress Controller | S-03 | Medio | DevOps / Platform |
| Log sanitization / PII masking | S-04, S-05 | Medio | Backend Dev |
| **Kotlin + Spring Boot** | SP-01 a SP-06, O-01 a O-06 | Senior | Backend Dev |
| Clean Architecture (DDD, SOLID) | Todos los servicios | Senior | Backend Dev + Arquitecto |
| Resilience4j (Circuit Breaker, Bulkhead) | SP-02, SP-03 | Senior | Backend Dev |
| Spring Data Redis | SP-01 | Medio | Backend Dev |
| Saga / Compensating Transactions | O-03 | Senior | Backend Dev + Arquitecto |
| Amazon EventBridge + SQS | O-05, W-01 a W-04 | Medio | Backend Dev / DevOps |
| Prometheus + Grafana | OB-01 | Medio | DevOps / Platform |
| k6 / load testing | SP-06, OB-02 | Medio | QA |
| Semgrep / SAST | S-05 | Básico | DevOps / Platform |

### Capacidades Críticas (bloqueadoras si faltan)

Las siguientes capacidades son **bloqueadoras**: si el equipo no las tiene, el MVP
no puede completarse en el tiempo estimado.

1. **Kotlin + Spring Boot + Clean Architecture** — es la base de todos los servicios. [CA-03]
2. **AWS EKS + CDK** — toda la infraestructura corre sobre EKS gestionado con CDK.
3. **Keycloak / OIDC** — sin un IdP funcional, el bloque de seguridad no avanza y bloquea todos los demás. [CA-01]
4. **Resilience4j (Circuit Breaker)** — mandato de diseño por [CA-02]. Sin esto, los legados no están protegidos.

### Gaps Identificados y Plan de Cierre

| Gap potencial | Plan |
|---------------|------|
| Keycloak en producción (operación día 2) | Incluir en el runbook operacional; evaluar IaC con Terraform Keycloak provider |
| PCI-DSS scope en profundidad | El Arquitecto Senior lidera la decisión; referencia PCI-DSS v4.0 en la documentación |
| Kotlin si el equipo viene de Java | La curva es mínima (1-2 días); Spring Boot es idéntico |

---

## Assumptions & Open Questions

- [assumption] El skill matrix asume que el equipo Vortexbird tiene las capacidades críticas cubiertas. Si hay gaps en Kotlin o EKS, deben declararse antes de iniciar construction para ajustar el plan.
