# NFR Validation Matrix — `skeleton-stock-query`

## Referencias upstream

| Artefacto | Ubicación | Qué aporta |
|---|---|---|
| `performance-requirements` | `construction/skeleton-stock-query/nfr-requirements/` | NFRs originales |
| `performance-design` | `construction/skeleton-stock-query/nfr-design/performance-design.md` | Budgets, targets, capacity |
| `scalability-design` | `construction/skeleton-stock-query/nfr-design/scalability-design.md` | HPA, scaling, bottlenecks |
| `reliability-design` | `construction/skeleton-stock-query/nfr-design/reliability-design.md` | CB, Bulkhead, degradación |
| `load-test-plan.md` | `operation/performance-validation/load-test-plan.md` | Plan de pruebas, criterios PASS |
| `test-results.md` | `operation/performance-validation/test-results.md` | Resultados ejecución (pendiente) |

---

## Matriz de Validación NFR

| NFR ID | Requisito | Target Diseño | Target Test | Resultado Test | Validación | Evidencia |
|---|---|---|---|---|---|---|
| **NFR-01** | Latencia p95 consulta stock ≤ 300ms | ≤ 300 ms (p95) | TC-01, TC-02: p95 ≤ 300ms | ⏳ Pendiente | ⏳ | Test results TC-01/TC-02 |
| **NFR-01** | Latencia p99 ≤ 500ms | ≤ 500 ms (p99) | TC-01, TC-02: p99 ≤ 500ms | ⏳ Pendiente | ⏳ | Test results TC-01/TC-02 |
| **NFR-01** | Acierto caché fresco < 15ms | < 15 ms | TC-01: cache hit latency p95 | ⏳ Pendiente | ⏳ | Test results TC-01 |
| **NFR-04** | Disponibilidad 99.9% | ≥ 99.9% | TC-01, TC-02: Error Rate < 0.1% | ⏳ Pendiente | ⏳ | Test results TC-01/TC-02 |
| **NFR-04** | RTO App < 15 min | < 15 min | RB-005 (rollout undo) | ⏳ Pendiente | ⏳ | Runbook validation |
| **NFR-04** | RTO Infra < 30 min | < 30 min | RB-006/007/008 (failover) | ⏳ Pendiente | ⏳ | Runbook validation |
| **NFR-04** | RPO Aurora < 1 min | < 1 min | PITR test | ⏳ Pendiente | ⏳ | DR test (Fase 2) |
| **NFR-04** | RPO Redis < 5 min | < 5 min | Snapshot diario | ⏳ Pendiente | ⏳ | Snapshot config |
| **NFR-06** | TLS 1.3 en tránsito | TLS 1.3 only | nmap ssl-enum-ciphers | ⏳ Pendiente | ⏳ | Security scan |
| **NFR-06** | Cifrado en reposo (KMS) | AES-256 | AWS Config / Console | ⏳ Pendiente | ⏳ | Infra validation |
| **NFR-10** | Circuit Breaker ERP | Ventana 30, wait 60s, threshold 50% | TC-06: CB opens/closes | ⏳ Pendiente | ⏳ | Test results TC-06 |
| **NFR-10** | Slow Call Detection | > 250ms = slow | TC-06: slow call detection | ⏳ Pendiente | ⏳ | Test results TC-06 |
| **NFR-11** | Rate Limiting 100 req/s | 100 req/s/consumer | TC-08: 101st = 429 | ⏳ Pendiente | ⏳ | Test results TC-08 |
| **NFR-11** | Rate Limit Headers | X-RateLimit-Limit, Retry-After | TC-08: headers check | ⏳ Pendiente | ⏳ | Test results TC-08 |
| **NFR-12** | Bulkhead ERP ≤ 20 concurrent | max 20 | TC-03: bulkhead rejects | ⏳ Pendiente | ⏳ | Test results TC-03 |
| **NFR-12** | Bulkhead Wait ≤ 100ms | max-wait 100ms | TC-03: bulkhead wait | ⏳ Pendiente | ⏳ | Test results TC-03 |
| **NFR-12** | SAP Timeout ≤ 250ms | 250ms connect/read | TC-05: SAP latency | ⏳ Pendiente | ⏳ | Test results TC-05 |

---

## Requisitos de Escalabilidad (Scalability)

| Requisito | Target | Validación | Estado |
|---|---|---|---|
| **Throughput Base** | 200 RPS sostenido | TC-01 | ⏳ |
| **Throughput Pico** | 800 RPS (2h) | TC-02 | ⏳ |
| **Throughput Burst** | 1500 RPS (5 min) | TC-03 | ⏳ |
| **HPA Scale-out** | 2→6 replicas < 3 min | TC-02 | ⏳ |
| **HPA Scale-in** | 6→2 replicas < 10 min | TC-02 | ⏳ |
| **Max Replicas** | 10 (HPA max) | TC-03 burst | ⏳ |
| **Node Group Scale** | 2→6 nodos EKS | TC-02/03 | ⏳ |
| **Redis Scale** | Vertical (cache.t4g.medium) | Manual | Config review |
| **Aurora Scale** | Serverless v2 (0.5-128 ACU) | Auto | Config review |

---

## Requisitos de Resiliencia (Reliability)

| Requisito | Target | Validación | Estado |
|---|---|---|---|
| **CB ERP** | Ventana 30, wait 60s, threshold 50% | TC-06 | ⏳ |
| **CB Slow Call** | > 250ms, rate > 50% | TC-06 | ⏳ |
| **CB Half-Open** | Cada 60s, 5 calls permitidos | TC-06 | ⏳ |
| **Bulkhead ERP** | Max 20 concurrent, wait 100ms | TC-03 | ⏳ |
| **Degradación Stale** | Fallback a stale-cache | TC-06, TC-07 | ⏳ |
| **Fail-Closed** | 503 + Retry-After: 5 si sin stale | TC-06, TC-05 | ⏳ |
| **Redis Failover** | < 60s, stale servido | TC-07 | ⏳ |
| **Aurora Failover** | < 30s, Keycloak restart < 5m | RB-007 | ⏳ |

---

## Requisitos de Seguridad (Security)

| Requisito | Target | Validación | Estado |
|---|---|---|---|
| **TLS 1.3** | Solo TLS 1.3 en ALB/Kong | nmap ssl-enum-ciphers | ⏳ |
| **mTLS Interno** | Service mesh / TLS interno | EKS + Kong config | ⏳ |
| **Rate Limiting** | 100 req/s/consumer | TC-08 | ⏳ |
| **Auth OAuth2/OIDC** | Keycloak 24, JWT RS256 | TC-01 auth flow | ⏳ |
| **Scope Enforcement** | `inventory:read` obligatorio | TC-01 403 sin scope | ⏳ |
| **PII en Logs** | 0 leaks (PCI-DSS) | Log query RB-018 | ⏳ |
| **Secrets Management** | Secrets Manager + ESO | ESO sync validation | ⏳ |
| **KMS Rotation** | Anual automática | AWS Config | ⏳ |

---

## Resumen de Validación

| Categoría | NFRs Totales | Validados | Pendientes | Bloqueados |
|---|---|---|---|---|
| **Performance (NFR-01)** | 3 | 0 | 3 | 0 |
| **Availability (NFR-04)** | 5 | 0 | 5 | 0 |
| **Encryption (NFR-06)** | 2 | 0 | 2 | 0 |
| **Resilience (NFR-10,12)** | 6 | 0 | 6 | 0 |
| **Rate Limiting (NFR-11)** | 2 | 0 | 2 | 0 |
| **Scalability** | 8 | 0 | 8 | 0 |
| **Resiliencia** | 8 | 0 | 8 | 0 |
| **Security** | 8 | 0 | 8 | 0 |
| **TOTAL** | **47** | **0** | **47** | **0** |

---

## Próximos Pasos para Validación

| Acción | Responsable | Plazo Estimado |
|---|---|---|
| 1. Desplegar Staging (`cdk deploy --all --profile andes-staging`) | Platform/DevOps | 1-2 días |
| 2. Deploy K8s Manifests (`kubectl apply -k deploy/`) | DevOps | 1 hora |
| 3. Configurar k6 Runners (3x c6g.xlarge) | DevOps | 30 min |
| 4. Ejecutar TC-01 (Baseline 30 min) | QA/Dev | 45 min |
| 5. Ejecutar TC-02 (Peak 2h + rampas) | QA/Dev | 2.5 horas |
| 6. Ejecutar TC-03 (Burst 5 min) | QA/Dev | 15 min |
| 7. Ejecutar TC-04 (Soak 4h) | QA/Dev | 4.5 horas |
| 8. Ejecutar TC-05 a TC-08 | QA/Dev | 2 horas |
| 9. Análisis resultados + Matriz NFR actualizada | QA/Dev + Tech Lead | 2 horas |
| 10. Decisión Go/No-Go Prod | Tech Lead + EM | 30 min |

**Tiempo Total Estimado**: ~1.5 - 2 días (incluyendo soak test)

---

## Decisión Go/No-Go para Producción

| Criterio | Go | No-Go |
|---|---|---|
| **Todos los NFR Críticos PASS** | ✓ | Cualquier FAIL en NFR-01, NFR-04, NFR-10, NFR-11, NFR-12 |
| **Error Rate < 0.1%** en TC-01/02 | ✓ | ≥ 0.1% |
| **p95 ≤ 300ms** en TC-01/02 | ✓ | > 300ms |
| **HPA Scale-out < 3 min** | ✓ | > 5 min |
| **CB/Bulkhead funcionan** (TC-03, TC-06) | ✓ | CB no abre, bulkhead no protege |
| **Failover Redis < 60s** | ✓ | > 2 min |
| **Failover Aurora < 30s** | ✓ | > 60s |
| **Rate Limiting correcto** | ✓ | 429 incorrecto o headers faltantes |
| **Zero Memory Leaks** (Soak 4h) | ✓ | Leak detectado |
| **Zero Data Loss** (failover tests) | ✓ | Cualquier pérdida |

> **Nota**: La validación completa requiere entorno staging desplegado. Esta matriz se actualizará con resultados reales post-ejecución.