# Deployment Execution Questions — `skeleton-stock-query`

## Preguntas pre-deployment (resueltas)

### 1. ¿Todos los pre-deployment checks pasan?
**Respuesta**: **Validados localmente** — CI pipeline verde (lint, test, coverage 95.3%, assemble, docker build), CDK synth EXIT 0. Checks reales contra AWS pendientes de credenciales.

### 2. ¿Se requieren migraciones de base de datos?
**Respuesta**: **No** — Primera deploy, tablas creadas por Aurora/Keycloak init. Futuras migraciones: Flyway/Liquibase (pendiente decisión).

### 3. ¿Servicios dependientes disponibles y saludables?
**Respuesta**: **Diseñado para ser autosuficiente** — Keycloak usa Aurora (mismo stack), Redis ElastiCache, SAP ERP (on-prem/VPN, mock en staging). External Secrets Operator sincroniza secrets tras deploy.

### 4. ¿Cuál es la ventana de despliegue?
**Respuesta**: **Horario bajo tráfico** (ej. martes-jueves 10:00-16:00 UTC), con on-call disponible. Staging: cualquier hora. Prod: requiere aprobación 2 ingenieros.

---

## Preguntas abiertas / decisiones pendientes

### 5. ¿Orden exacto de despliegue CDK?
**Actual**: Network → Security → Data → Compute (CDK resuelve deps automáticamente).
**Pregunta**: ¿Deploy paralelo donde sea posible (Network+Security simultáneo)?
**Recomendación**: Sí, `cdk deploy AndesRetailNetworkStack AndesRetailSecurityStack --concurrency 2` luego Data → Compute.

### 6. ¿Validación de ExternalSecrets post-deploy?
**Pregunta**: ¿Esperar a que todos los `ExternalSecret` marquen `SecretSynced: True` antes de deploy app?
**Recomendación**: Sí, añadir wait en deployment-log (timeout 5 min). Si falla, rollback app y revisar IAM/SecretsManager.

### 7. ¿Warm-up de pods pre-tráfico?
**Pregunta**: ¿Añadir `preStop` hook + `terminationGracePeriodSeconds: 30` + readiness gate?
**Actual**: `terminationGracePeriodSeconds: 30` en deployment.yaml. `preStop` hook pendiente (sleep 10 para drain connections).
**Recomendación**: Añadir `preStop` con `sleep 10` en próxima iteración.

### 8. ¿Smoke tests automatizados en pipeline CD?
**Pregunta**: ¿Integrar smoke tests como step post-deploy en GitHub Actions CD workflow?
**Recomendación**: Sí, step `smoke-tests` que ejecute `smoke-test-results.md` suite. Fail → auto-rollback (`kubectl rollout undo`).

### 8. ¿Canary deployment para producción?
**Pregunta**: ¿Implementar Argo Rollouts para canary 10%→50%→100% en prod?
**Actual**: Rolling Update con `maxUnavailable: 0`. Canary diferido (fase 2).
**Decisión**: Mantener Rolling para v1.0, canary en v1.1.

### 9. ¿Notificaciones deploy (Slack/Teams)?
**Pregunta**: ¿Webhook Slack para `#andes-deploys` y `#andes-alerts`?
**Recomendación**: Configurar en GitHub Actions CD workflow + `kubectl` post-deploy hooks.

### 10. ¿Métricas de éxito deploy (KPIs)?
| KPI | Objetivo | Medición |
|---|---|---|
| Deploy time (staging) | < 45 min | Timestamp start→end |
| Deploy time (prod) | < 60 min (con canary) | Timestamp start→end |
| Rollback time | < 5 min (app), < 30 min (infra) | Timestamp issue→restored |
| Smoke test pass rate | 100% críticos | Test results |
| Post-deploy error rate | 0% (primeras 2h) | Logs + métricas |
| Rollback rate | 0% | Historial deploys |

### 11. ¿Database migration strategy para futuras versiones?
- **Additive only**: Solo añadir columnas/tablas, nunca borrar/renombrar en uso
- **Backward compatible**: App vN+1 debe funcionar con schema vN
- **Tooling**: Flyway (SQL) o Liquibase (XML/YAML) — decisión pendiente
- **Execution**: Pre-deploy step en CD pipeline (antes de app rollout)

### 12. ¿Feature flags para deploy seguro?
**Flags definidas** (preparadas, no implementadas):
- `stock.stale-cache.enabled` (default: true)
- `stock.circuit-breaker.enabled` (default: true)
- `stock.new-mapper.version` (default: false)

**Pregunta**: ¿Implementar Spring Cloud Config / AWS AppConfig ahora o diferir?
**Recomendación**: Diferir a v1.1 — flags preparadas en código, activación via configmap basta para v1.0.

### 13. ¿Disaster Recovery test tras primer deploy?
**Pregunta**: ¿Programar test de restore snapshot Aurora/Redis tras primer deploy a staging?
**Recomendación**: Sí, dentro de 1 semana post-deploy. Documentar en `rollback-runbook.md`.

### 14. ¿Cost monitoring baseline?
**Pregunta**: ¿Activar AWS Budgets + Cost Explorer tags tras primer deploy?
**Recomendación**: Sí, tag `Project=AndesRetail` en todos los recursos (CDK `Tags.of(stack).add()`). Budget alert 80% forecast.

---

## Respuestas registradas (auditoría)

| Pregunta | Respuesta | Fecha | Responsable |
|---|---|---|---|
| Pre-deployment checks | Validados local (CI, CDK synth) | 2026-10-18 | Pipeline Deploy |
| DB Migrations | No (primera deploy) | 2026-10-18 | Pipeline Deploy |
| Dependent Services | Keycloak→Aurora, Redis, SAP mock | 2026-10-18 | Pipeline Deploy |
| Deployment Window | Staging: anytime; Prod: business hours + 2 approvals | 2026-10-18 | Pipeline Deploy |
| CDK Deploy Order | Network+Security paralelos → Data → Compute | 2026-10-18 | Pipeline Deploy |
| ExternalSecrets Wait | Sí, wait 5 min pre-app-deploy | 2026-10-18 | Pipeline Deploy |
| PreStop Hook | Pendiente (añadir sleep 10 en v1.1) | 2026-10-18 | Pipeline Deploy |
| Smoke Tests in CD | Sí, step post-deploy con auto-rollback | 2026-10-18 | Pipeline Deploy |
| Canary Prod | Diferido v1.1 (Rolling v1.0) | 2026-10-18 | Pipeline Deploy |
| Slack Notifications | Pendiente (webhook + workflow step) | 2026-10-18 | Pipeline Deploy |
| Deploy KPIs | Definidos (time, rollback, error rate) | 2026-10-18 | Pipeline Deploy |
| DB Migration Strategy | Flyway/Liquibase pendiente decisión, additive-only | 2026-10-18 | Pipeline Deploy |
| Feature Flags | 3 preparadas, activación via ConfigMap v1.0 | 2026-10-18 | Pipeline Deploy |
| DR Test | Programar 1 semana post-staging-deploy | 2026-10-18 | Pipeline Deploy |
| Cost Monitoring | AWS Budgets + tags (pendiente baseline) | 2026-10-18 | Pipeline Deploy |