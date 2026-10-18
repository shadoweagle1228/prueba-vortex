# Log Queries — `skeleton-stock-query`

## Referencias upstream

| Artefacto | Ubicación | Qué aporta |
|---|---|---|
| `security-design` | `construction/skeleton-stock-query/nfr-design/security-design.md` | Log sanitization, correlation-id |
| `performance-design` | `construction/skeleton-stock-query/nfr-design/performance-design.md` | Latencia budgets |
| `monitoring-design` | `construction/skeleton-stock-query/nfr-design/monitoring-design.md` | Log aggregation, retention |

---

## Configuración CloudWatch Logs

| Log Group | Retención | Patrón | KMS |
|---|---|---|---|
| `/aws/eks/andes-retail-staging/cluster` | 30 días | Kubernetes audit, control plane | CMK `andes-retail` |
| `/aws/eks/andes-retail-staging/containers` | 30 días | App logs (JSON), correlation-id | CMK `andes-retail` |
| `/aws/kong/andes-retail-staging` | 30 días | Access logs, rate limit logs | CMK `andes-retail` |
| `/aws/keycloak/andes-retail-staging` | 90 días | Auth logs, admin actions | CMK `andes-retail` |

---

## Saved Queries (CloudWatch Logs Insights)

### 1. Errores 5xx en stock-price-adapter
```sql
fields @timestamp, @message, @logStream, correlationId, level
| filter @logStream like /stock-price-adapter/
  and level in ["ERROR", "WARN"]
  and @message like /500|503|502/
| sort @timestamp desc
| limit 100
```
**Uso**: Investigar errores 5xx recientes
**Alertas relacionadas**: `CRIT-STOCK-ERROR-RATE`, `CRIT-STOCK-CB-OPEN`

### 2. Circuit Breaker Events
```sql
fields @timestamp, @message, correlationId, circuitBreakerState
| filter @logStream like /stock-price-adapter/
  and @message like /CircuitBreaker|circuitBreaker/
| sort @timestamp desc
| limit 50
```
**Uso**: Rastrear apertura/cierre de Circuit Breaker SAP
**Alertas relacionadas**: `CRIT-STOCK-CB-OPEN`, `WARN-STOCK-CB-HALF-OPEN`

### 3. Bulkhead Exhaustion
```sql
fields @timestamp, @message, correlationId, availableConcurrentCalls
| filter @logStream like /stock-price-adapter/
  and @message like /Bulkhead|bulkhead|exhausted|rejected/
| sort @timestamp desc
| limit 50
```
**Uso**: Detectar agotamiento de bulkhead ERP
**Alertas relacionadas**: `CRIT-STOCK-BULKHEAD-EXHAUSTED`

### 4. Cache Miss / Stale / Fail-Closed
```sql
fields @timestamp, @message, correlationId, cacheHit, stockSource
| filter @logStream like /stock-price-adapter/
  and (cacheHit = false or stockSource in ["stale-cache", "fail-closed"])
| sort @timestamp desc
| limit 100
```
**Uso**: Analizar degradación a stale-cache y fail-closed
**Alertas relacionadas**: `WARN-STOCK-CACHE-HIT-RATIO`, `CRIT-STOCK-DEGRADATION`

### 5. Authentication Failures (401/403)
```sql
fields @timestamp, @message, correlationId, statusCode, xCorrelationId
| filter @logStream like /stock-price-adapter/
  and statusCode in [401, 403]
| sort @timestamp desc
| limit 100
```
**Uso**: Detectar ataques de fuerza bruta, tokens expirados, scopes incorrectos
**Alertas relacionadas**: Seguridad, `WARN-STOCK-AUTH-FAILURES`

### 6. Correlation ID Tracing (End-to-End)
```sql
fields @timestamp, @logStream, @message, correlationId, xCorrelationId
| filter correlationId = "TARGET_CORRELATION_ID"
| sort @timestamp asc
```
**Uso**: Trazar request completo a través de todos los componentes
**Parámetro**: Reemplazar `TARGET_CORRELATION_ID` con UUID real

### 7. SAP ERP Calls (Latency & Errors)
```sql
fields @timestamp, @message, correlationId, sapLatencyMs, sapStatusCode, sku
| filter @logStream like /stock-price-adapter/
  and @message like /SAP|erp|SapClient/
| sort @timestamp desc
| limit 100
```
**Uso**: Analizar latencia y errores hacia SAP ERP
**Alertas relacionadas**: `WARN-STOCK-SAP-LATENCY`, `CRIT-STOCK-CB-OPEN`

### 8. Rate Limiting Events (Kong)
```sql
fields @timestamp, @message, consumer, remoteAddr, rateLimitLimit, rateLimitRemaining
| filter @logStream like /kong/
  and @message like /rate.limit|429|rate.limit.exceeded/
| sort @timestamp desc
| limit 100
```
**Uso**: Analizar rate limiting, identificar consumers abusivos
**Alertas relacionadas**: NFR-11

### 9. Keycloak Authentication Events
```sql
fields @timestamp, @message, realm, clientId, userId, eventType, error
| filter @logStream like /keycloak/
  and eventType in ["LOGIN", "LOGIN_ERROR", "TOKEN_EXCHANGE", "CLIENT_REGISTER"]
| sort @timestamp desc
| limit 100
```
**Uso**: Auditar autenticación, detectar ataques, tokens expirados

### 10. Correlation ID Propagation Check
```sql
fields @timestamp, @logStream, @message, correlationId, xCorrelationId
| filter @logStream like /stock-price-adapter/
  and correlationId != xCorrelationId
  and xCorrelationId != ""
| sort @timestamp desc
| limit 50
```
**Uso**: Detectar propagación incorrecta de correlation-id (NFR-08)
**Debe retornar**: 0 resultados (correlationId == xCorrelationId siempre)

### 11. PII Leakage Detection (NFR-08)
```sql
fields @timestamp, @logStream, @message, correlationId
| filter @logStream like /stock-price-adapter/
  and @message like /(?i)(password|token|secret|credit|card|pan|ssn|dni|passport)/
| sort @timestamp desc
| limit 50
```
**Uso**: Detectar fuga de PII en logs (debe retornar 0)
**Compliance**: PCI-DSS, CA-01

### 12. Slow Requests (Latency > 300ms)
```sql
fields @timestamp, @message, correlationId, latencyMs, uri, method, statusCode
| filter @logStream like /stock-price-adapter/
  and latencyMs > 300
| sort @timestamp desc
| limit 50
```
**Uso**: Identificar requests lentos para optimización
**Alertas relacionadas**: `CRIT-STOCK-LATENCY-P95`, `WARN-STOCK-LATENCY-P95`

---

## Queries de Correlación Multi-Log-Group

### 11. Trace completo request (App + Kong + Keycloak)
```sql
fields @timestamp, @logStream, @message, correlationId
| filter correlationId = "TARGET_CORRELATION_ID"
  and @logStream in ["/aws/eks/andes-retail-staging/containers", "/aws/kong/andes-retail-staging", "/aws/keycloak/andes-retail-staging"]
| sort @timestamp asc
```
**Uso**: Vista end-to-end de un request a través de toda la cadena

### 12. Rate Limiting por Consumer (Kong + App)
```sql
fields @timestamp, consumer, @message, rateLimitLimit, rateLimitRemaining
| filter @logStream like /kong/
  and @message like /rate.limit/
| stats count() as requests by consumer, bin(5m)
| sort requests desc
```
**Uso**: Identificar top consumers, planificar capacity

---

## Export y Automatización

| Query | Export | Schedule | Destino |
|---|---|---|---|
| Errores 5xx | CSV | Cada 1h | S3 `andes-retail-logs/errors/` |
| Circuit Breaker | JSON | Cada 30 min | S3 `andes-retail-logs/cb/` |
| Auth Failures | JSON | Cada 15 min | S3 `andes-retail-logs/auth/` |
| PII Detection | JSON | Cada 1h | S3 `andes-retail-logs/pii/` + Alert immediate |
| Slow Requests | Parquet | Cada 1h | S3 `andes-retail-logs/performance/` |

**Automatización**: EventBridge rule → Lambda → Logs Insights query → S3 + SNS alert si resultados > 0 (para PII, CB open, etc.)

---

## Retención y Compliance

| Log Type | Retención | Compliance | Encriptación |
|---|---|---|---|
| App logs | 30 días | PCI-DSS (1 año para audit) | CMK `andes-retail` |
| Kong access | 30 días | PCI-DSS | CMK `andes-retail` |
| Keycloak auth | 90 días | PCI-DSS, GDPR | CMK `andes-retail` |
| Kubernetes audit | 90 días | PCI-DSS, SOC2 | CMK `andes-retail` |
| Export S3 | 1 año (Glacier) | Audit trail | SSE-S3 + CMK |

---

## Métricas derivadas de Logs (Log-based Metrics)

| Métrica | Filtro | Nombre CloudWatch | Alarma |
|---|---|---|---|
| `StockAdapter_5xx_Count` | `level=ERROR status=5..` | `StockAdapter/5xxCount` | `CRIT-STOCK-ERROR-RATE` |
| `StockAdapter_CB_Open` | `circuitBreakerState=OPEN` | `StockAdapter/CBOpen` | `CRIT-STOCK-CB-OPEN` |
| `StockAdapter_Bulkhead_Rejected` | `bulkhead=REJECTED` | `StockAdapter/BulkheadRejected` | `CRIT-STOCK-BULKHEAD-EXHAUSTED` |
| `StockAdapter_CacheMiss` | `cacheHit=false` | `StockAdapter/CacheMiss` | `WARN-STOCK-CACHE-HIT-RATIO` |
| `StockAdapter_AuthFailure` | `status=401|403` | `StockAdapter/AuthFailure` | `WARN-STOCK-AUTH-FAILURES` |
| `Kong_RateLimit_Exceeded` | `rateLimitExceeded=true` | `Kong/RateLimitExceeded` | `INFO-KONG-RATELIMIT` |
| `Keycloak_LoginFailure` | `eventType=LOGIN_ERROR` | `Keycloak/LoginFailure` | `WARN-KEYCLOAK-AUTH-FAILURES` |