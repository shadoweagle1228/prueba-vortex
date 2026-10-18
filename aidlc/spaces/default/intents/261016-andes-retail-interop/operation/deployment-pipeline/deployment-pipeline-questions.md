# Deployment Pipeline Questions — `skeleton-stock-query`

## Preguntas de clarificación (resueltas)

### 1. ¿Qué estrategia de despliegue se usa?
**Respuesta**: **Rolling Update** (configurado en `deploy/k8s/deployment.yaml`: `maxUnavailable: 0`, `maxSurge: 1`). Canary opcional para releases de alto riesgo (futuro: Argo Rollouts).

### 2. ¿Cuál es el flujo de promoción de entornos?
**Respuesta**:
- **staging**: Deploy automático tras CI verde en `main` (`cdk deploy --profile staging` + `kubectl apply`)
- **prod**: Deploy manual con aprobación 2 ingenieros (GitHub Environment `production`), tag semver `vX.Y.Z`

### 3. ¿Qué aprobación se requiere para producción?
**Respuesta**: Aprobación manual de 2 ingenieros senior vía GitHub Environments `production`. Requiere staging validado > 30 min sin incidentes.

### 4. ¿Cuál es el procedimiento de rollback?
**Respuesta**: Documentado en `rollback-runbook.md`:
- **App (K8s)**: `kubectl rollout undo` (< 3 min)
- **Config**: `kubectl apply -f deploy/k8s/configmap.yaml` + rollout restart
- **Infra (CDK)**: `cdk destroy` por stack (orden inverso) — solo catástrofe, preferir fix forward

### 5. ¿Qué feature flags se prevén?
**Respuesta** (preparado, no implementado):
- `stock.stale-cache.enabled` (default: true)
- `stock.circuit-breaker.enabled` (default: true)
- `stock.new-mapper.version` (default: false)
Tooling: Spring Cloud Config / AWS AppConfig (futuro)

---

## Preguntas abiertas / decisiones pendientes

### 6. ¿Canary deployment para producción?
- **Opción A**: Implementar Argo Rollouts ahora (esfuerzo medio)
- **Opción B**: Mantener Rolling Update, añadir canary solo si hay incidente
- **Recomendación**: **Opción B** — Rolling Update con `maxUnavailable: 0` + health checks robustos cubre NFR-04. Canary cuando haya métricas de negocio críticas.

### 7. ¿Pipeline CD automatizado (GitHub Actions)?
- **Actual**: `cdk deploy` manual desde CLI con perfiles AWS
- **Deseado**: Workflow `.github/workflows/cd.yml` con:
  - `deploy-staging` automático tras CI verde (OIDC role)
  - `deploy-prod` manual con `environment: production` (approval gate)
- **Esfuerzo**: Medio (configurar OIDC provider + IAM roles en ambas cuentas)
- **Decisión**: **Implementar en próximo sprint** — reduce riesgo humano y acelera lead time.

### 8. ¿Estrategia de versionado y tagging?
- **Actual**: `version = "0.1.0"` en `build.gradle.kts` (hardcoded)
- **Propuesta**: Semantic Release / `git tag vX.Y.Z` → Gradle lee tag → `version = tag`
- **Decisión**: **Pendiente** — definir antes de primer release a prod.

### 9. ¿Gestión de secretos en CD?
- **Actual**: Secrets en `deploy/k8s/` (no versionados) + `ExternalSecretsOperator` en CDK
- **Falta**: 
  - `stock-price-adapter-secrets` (SAP credentials) — sincronizar desde Secrets Manager
  - `stock-price-adapter-metrics-token` (Prometheus scrape) — generar y sincronizar
  - JWT keys para Kong (RS256) — sincronizar desde Keycloak
- **Decisión**: **Completar ExternalSecrets** antes de primer deploy a staging.

### 10. ¿Disaster Recovery / Failover región?
- **Actual**: Single region (`us-east-1`), Multi-AZ dentro de región
- **RTO/RPO objetivo**: RTO < 4h, RPO < 1h (Aurora/Redis snapshots diarios)
- **Futuro**: Active-passive en `us-west-2` (Route 53 failover, Aurora Global DB, Redis Global Datastore)
- **Decisión**: **Fase 2** — documentar runbook DR, probar restore snapshot trimestral.

### 11. ¿Observabilidad post-deploy automática?
- **Deseado**: Job post-deploy que valide:
  - Health checks 200
  - Smoke test endpoint stock
  - Métricas scrapeando (CB state = CLOSED)
  - Alertas silenciosas si falla
- **Implementación**: Script `validate-deploy.sh` + step en CD workflow
- **Decisión**: **Añadir en workflow CD** cuando se implemente.

### 12. ¿Cost control / presupuestos?
- **Actual**: Sin alertas de coste
- **Deseado**: AWS Budgets + alertas > 80% forecast
- **Decisión**: **Configurar tras primer deploy** (conocer baseline real).

---

## Respuestas registradas (auditoría)

| Pregunta | Respuesta | Fecha | Responsable |
|---|---|---|---|
| Deployment Strategy | Rolling Update (maxUnavailable=0, maxSurge=1) | 2026-10-18 | Lead Dev |
| Environment Promotion | staging (auto main), prod (manual 2 approvals) | 2026-10-18 | Lead Dev |
| Production Approval | GitHub Environment `production`, 2 reviewers | 2026-10-18 | Lead Dev |
| Rollback Procedure | `rollback-runbook.md` (K8s <3min, CDK catástrofe) | 2026-10-18 | Lead Dev |
| Feature Flags | 3 definidas (stale-cache, CB, mapper v2), tooling futuro | 2026-10-18 | Lead Dev |
| Canary Production | Diferido (Rolling suficiente con health checks) | 2026-10-18 | Lead Dev |
| CD Automation | Pendiente (próximo sprint: OIDC + workflow) | 2026-10-18 | Lead Dev |
| Versioning | Pendiente (Semantic Release / git tag) | 2026-10-18 | Lead Dev |
| Secrets Management | ExternalSecretsOperator (pendiente completar secrets) | 2026-10-18 | Lead Dev |
| DR / Failover | Fase 2 (documentar runbook, probar restore) | 2026-10-18 | Lead Dev |
| Post-deploy Validation | Pendiente (script + CD step) | 2026-10-18 | Lead Dev |
| Cost Control | Pendiente (AWS Budgets tras baseline) | 2026-10-18 | Lead Dev |