# Deployment Strategy — `skeleton-stock-query`

## Estrategia seleccionada: **Rolling Update con Canary opcional**

### Justificación

| Criterio | Rolling | Blue/Green | Canary |
|---|---|---|---|
| Costo (infra) | Bajo (1x) | Alto (2x) | Medio (1.1-1.5x) |
| Tiempo deploy | ~5 min | ~3 min (+ switch) | ~10-15 min |
| Riesgo rollback | Bajo (maxSurge 1) | Muy bajo | Bajo |
| Validación real | Tráfico real gradual | Solo pre-switch | Tráfico real controlado |
| **Requisito NFR-04 (99.9%)** | ✅ PDB + maxUnavailable=0 | ✅ | ✅ |
| **Requisito CA-02 (backpressure)** | ✅ Bulkhead + CB | ✅ | ✅ |

**Decisión**: **Rolling Update** como estrategia base (configurada en `deploy/k8s/deployment.yaml`: `maxUnavailable: 0`, `maxSurge: 1`). **Canary opcional** para releases de mayor riesgo (feature flags + Istio/Argo Rollouts futuro).

---

## Configuración Rolling Update (actual)

```yaml
# deploy/k8s/deployment.yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxUnavailable: 0      # Nunca bajar réplicas sanas (NFR-04)
    maxSurge: 1            # 1 pod extra durante rollout
```

### Comportamiento

1. Nuevo ReplicaSet crea 1 pod (`maxSurge: 1`)
2. Espera `readinessProbe` OK (`/actuator/health/readiness`)
3. Pod nuevo recibe tráfico
4. Pod viejo termina (drain graceful, `terminationGracePeriodSeconds: 30`)
4. Repite hasta `replicas` deseadas

### Timeouts y health checks

| Parámetro | Valor | Justificación |
|---|---|---|
| `startupProbe` | 24 × 5s = 120s | Arranque Spring + JWKS resolution |
| `readinessProbe` | 10s interval, 3s timeout | `/actuator/health/readiness` (solo `readinessState`) |
| `livenessProbe` | 30s interval, 3s timeout | `/actuator/health/liveness` (solo `livenessState`) |
| `terminationGracePeriodSeconds` | 30s | Drain connections, flush logs |
| `maxUnavailable: 0` | Nunca 0 pods listos | NFR-04 99.9% |

---

## Canary (futuro - opcional)

### Cuándo usar
- Cambios en lógica de negocio crítica (precios, stock)
- Migración de esquema BD
- Nueva integración ERP

### Implementación objetivo (Argo Rollouts / Istio)
```yaml
# Ejemplo concepto (no implementado)
apiVersion: argoproj.io/v1alpha1
kind: Rollout
spec:
  replicas: 6
  strategy:
    canary:
      steps:
        - setWeight: 10
        - pause: {duration: 5m}
        - setWeight: 30
        - pause: {duration: 10m}
        - setWeight: 50
        - pause: {duration: 10m}
        - setWeight: 100
  analysis:
    templates:
      - templateName: success-rate
    args:
      - name: service-name
        value: stock-price-adapter
```

### Métricas de decisión canary
| Métrica | Umbral abort | Acción |
|---|---|---|
| Error rate 5xx | > 1% | Rollback automático |
| Latencia p95 | > 500ms | Pausa + análisis |
| Circuit Breaker opens | > 0 | Rollback inmediato |

---

## Promoción de entornos

| Entorno | Trigger | Aprobación | Validación |
|---|---|---|---|
| **staging** | Push a `main` (CI verde) | Automática | Smoke tests + health checks |
| **prod** | Tag `vX.Y.Z` o manual | Manual (2 aprobadores) | Canary 10% → 50% → 100% + pruebas humo |

### Gates de promoción automática (staging)
- ✅ CI pipeline verde (lint, test, coverage ≥80%, assemble, docker build)
- ✅ `cdk deploy staging` exitoso
- ✅ `kubectl rollout status deployment/stock-price-adapter -n andes-retail` (timeout 10m)
- ✅ Health checks: `/actuator/health/liveness` + `/readiness` = 200
- ✅ Smoke test: `GET /v1/inventory/stock/SMOKE-TEST` con token válido → 200

### Gates de promoción manual (prod)
- ✅ Staging validado > 30 min sin incidentes
- ✅ Aprobación 2 ingenieros senior (GitHub Environments `production`)
- ✅ Canary 10% → 50% → 100% con métricas verdes
- ✅ Rollback runbook revisado y accesible

---

## Feature Flags (preparado para futuro)

| Flag | Propósito | Default | Tooling |
|---|---|---|---|
| `stock.stale-cache.enabled` | Activar/desactivar fallback stale | `true` | Spring Cloud Config / AWS AppConfig |
| `stock.circuit-breaker.enabled` | Activar CB ERP | `true` | Resilience4j runtime config |
| `stock.new-mapper.version` | Nuevo mapper ACL v2 | `false` | Spring Cloud Config |

---

## Matriz de riesgo por tipo de cambio

| Tipo de cambio | Estrategia | Validación extra |
|---|---|---|
| Bug fix / hotfix | Rolling | Solo staging smoke test |
| Feature nueva (no breaking) | Rolling | Staging + integration tests |
| Cambio breaking API | Canary (10→50→100) | Contrato tests + canary metrics |
| Migración BD | Blue/Green (futuro) | Snapshot + dual-write + switch |
| Infra (CDK stacks) | Rolling (stacks independientes) | `cdk diff` + staging deploy |
| Config (ConfigMap/Secret) | Rolling (restart pods) | Validación valores en staging |