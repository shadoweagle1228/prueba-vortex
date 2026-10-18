# CI Pipeline Questions — `skeleton-stock-query`

## Preguntas de clarificación (resueltas)

### 1. ¿Qué herramienta CI se usa?
**Respuesta**: GitHub Actions (`.github/workflows/ci.yml` ya existe y funciona).

### 2. ¿Cuál es la estrategia de ramas?
**Respuesta**: 
- `main` protegida, merges solo vía Pull Request
- `feature/*` para desarrollo, PR hacia `main`
- `release/*` opcional para versionado

### 3. ¿Qué quality gates son requeridos antes de merge?
**Respuesta** (todos implementados y bloqueantes):
- `ktlintCheck`: 0 violaciones
- `test`: 0 fallos / 0 errores (58 tests)
- `jacocoTestCoverageVerification`: líneas ≥ 80 % (actual 95,30 %)
- `assemble` / `bootJar`: JAR generado
- `docker buildx build --platform linux/arm64`: build exitoso
- `py -3 infra/app.py`: síntesis CDK EXIT 0

### 4. ¿Qué repositorios de artefactos se usan?
**Respuesta**:
- **Imágenes contenedor**: GitHub Container Registry (ghcr.io) o ECR (para deploy AWS) — *pendiente configurar push en job `docker`*
- **Librerías/dependencias**: Maven Central + Spring repos (via Gradle)
- **Reportes**: GitHub Actions Artifacts (JaCoCo, JUnit XML) — 7 días retención

---

## Preguntas abiertas / decisiones pendientes

### 5. ¿Push automático de imagen a registry en CI?
- **Opción A**: Push a `ghcr.io` en job `docker` tras `build-and-test` exitoso (requiere `GITHUB_TOKEN` + `packages: write`)
- **Opción B**: Push a ECR solo en pipeline de deploy manual (`cdk deploy` usa ECR)
- **Decisión pendiente**: Recomendación **Opción A** para staging automático; ECR para prod.

### 6. ¿Branch strategy para releases?
- **Actual**: Solo `main` protegida, merges por PR
- **Futuro**: ¿`release/vX.Y.Z` tags + GitHub Releases? ¿Versionado semántico automático?

### 7. ¿Quality gates adicionales deseados?
| Gate | Implementación | Esfuerzo | Prioridad |
|---|---|---|---|
| Dependency Check (OWASP) | `./gradlew dependencyCheckAnalyze` | Bajo | Media |
| Container Scan (Trivy) | Job `docker` + `trivy-action` | Bajo | Media |
| SAST (SpotBugs+FindSecBugs) | Plugin Gradle + job CI | Medio | Baja |
| Contract Test (Pact) | Solo si hay consumers externos | Alto | Baja |

### 8. ¿Notificaciones?
- **Actual**: Solo estado PR (GitHub nativo)
- **Deseado**: Slack/Teams en fallo de `main`? ¿Webhook personalizado?

### 9. ¿Entornos de despliegue CI/CD?
| Entorno | Trigger | Aprobación | Cuenta AWS |
|---|---|---|---|
| **staging** | Push a `main` (tras CI verde) | Automática | Cuenta staging |
| **production** | Tag `vX.Y.Z` o manual | Manual (approval gate) | Cuenta prod |

### 10. ¿Secrets management en CI?
- **Actual**: `GITHUB_TOKEN` para Actions artifacts
- **Faltante**: AWS credentials para `cdk deploy` (OIDC provider + IAM role recomendado), Docker registry credentials si push externo

---

## Respuestas registradas (para auditoría)

| Pregunta | Respuesta | Fecha | Responsable |
|---|---|---|---|
| CI Tool | GitHub Actions | 2026-10-18 | Lead Developer |
| Branch Strategy | GitHub Flow (main + feature/*) | 2026-10-18 | Lead Developer |
| Quality Gates | Lint, Test, Coverage ≥80%, Assemble, Docker ARM64, CDK Synth | 2026-10-18 | Lead Developer |
| Artifact Repos | GitHub Actions Artifacts (reports), ghcr.io/ECR (images, pendiente) | 2026-10-18 | Lead Developer |
| Image Push | Pendiente (recomendado ghcr.io en CI, ECR en deploy) | — | — |
| Release Strategy | Pendiente (GitHub Flow + tags semver) | — | — |
| Additional Gates | Dependency Check, Trivy (planificados) | — | — |
| Notifications | GitHub nativo (solo PR status) | 2026-10-18 | — |
| Deploy Environments | staging (auto main), prod (manual tag) | — | — |
| Secrets | GITHUB_TOKEN; AWS OIDC pendiente | 2026-10-18 | — |