# Tracing Configuration — `skeleton-stock-query`

## Referencias upstream

| Artefacto | Ubicación | Qué aporta |
|---|---|---|
| `performance-design` | `construction/skeleton-stock-query/nfr-design/performance-design.md` | Latencia budgets, golden signals |
| `security-design` | `construction/skeleton-stock-query/nfr-design/security-design.md` | Correlation-id, log sanitization |
| `monitoring-design` | `construction/skeleton-stock-query/nfr-design/monitoring-design.md` | Golden signals, SLOs |

---

## Estrategia de Trazabilidad Distribuida

### Objetivo
- **End-to-end visibility**: Request externo → Kong → App → Redis → SAP → Response
- **Correlation ID**: `X-Correlation-ID` (UUID v4) propagado en toda la cadena
- **Sampling**: 100% para errors, 10% para success (costo/beneficio)
- **Backend**: AWS X-Ray + OpenTelemetry Collector (sidecar) + Prometheus exemplars

### Arquitectura de Trazabilidad

```
Client (canal ecommerce/tienda/operador)
    │
    ▼
Kong Ingress Controller
    │  X-Correlation-ID header
    │  X-B3-TraceId, X-B3-SpanId (W3C)
    ▼
stock-price-adapter (Spring Boot)
    │  CorrelationIdFilter (genera/propaga X-Correlation-ID)
    │  Micrometer Tracing + OpenTelemetry
    │  Spans: HTTP Server, Redis, SAP HTTP Client
    ▼
    ├─▶ Redis (ElastiCache) ──▶ Span: Redis GET/SET
    │
    └─▶ SAP ERP (HTTP) ──▶ Span: HTTP Client (WebClient)
        │  X-Correlation-ID header propagado
        ▼
    Response ◀────────────────────
    │
    ▼
Kong (rate limit, auth validation)
    │
    ▼
Client
```

---

## Instrumentación Spring Boot (stock-price-adapter)

### Dependencias (build.gradle.kts)
```kotlin
// Micrometer Tracing + OpenTelemetry
implementation("io.micrometer:micrometer-tracing-bridge-otel")
implementation("io.opentelemetry:opentelemetry-exporter-otlp")
implementation("io.opentelemetry:opentelemetry-sdk-extension-autoconfigure")
// Micrometer Registry Prometheus (ya incluido)
implementation("io.micrometer:micrometer-registry-prometheus")
```

### Configuración (application.yml)
```yaml
management:
  tracing:
    sampling:
      probability: 0.1  # 10% sampling para success, 100% para errors
    enabled: true
  otlp:
    tracing:
      endpoint: http://otel-collector:4318/v1/traces  # OTLP HTTP
    metrics:
      export:
        endpoint: http://otel-collector:4318/v1/metrics

# Correlation ID propagation (ya implementado en CorrelationIdFilter)
# Spring Cloud Sleuth / Micrometer Tracing usa headers W3C TraceContext
```

### CorrelationIdFilter (ya implementado)
- Genera UUID v4 si no viene `X-Correlation-ID`
- Propaga en header respuesta `X-Correlation-ID`
- Guarda en MDC + Request Attribute
- Longitud máx 128 chars

### Headers W3C TraceContext (adicional)
```yaml
# Spring Boot 3.2+ Micrometer Tracing
management:
  tracing:
    propagation:
      type: W3C  # Traceparent, Tracestate headers
```

---

## Instrumentación Kong

### Plugin OpenTelemetry (Kong 3.x)
```yaml
# KongPlugin OpenTelemetry
apiVersion: configuration.konghq.com/v1
kind: KongPlugin
metadata:
  name: opentelemetry
  namespace: kong
plugin: opentelemetry
config:
  endpoint: "http://otel-collector.kong.svc:4318/v1/traces"
  resource_attributes:
    "service.name": "kong-ingress"
    "deployment.environment": "staging"
  header_type: "w3c"  # traceparent/tracestate
  sample_rate: 0.1
```

### Headers propagados
- `traceparent` (W3C): `00-<trace-id>-<span-id>-01`
- `tracestate` (W3C): vendor-specific
- `X-Correlation-ID`: UUID propio (legacy compatibility)

---

## Instrumentación Keycloak

### OpenTelemetry Support (Keycloak 24+)
```yaml
# keycloak.conf o env vars
KC_OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector.keycloak.svc:4318/v1/traces
KC_OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf
KC_OTEL_TRACES_SAMPLER=parentbased_traceidratio
KC_OTEL_TRACES_SAMPLER_ARG=0.1
KC_OTEL_RESOURCE_ATTRIBUTES=service.name=keycloak,deployment.environment=staging
```

### Spans generados
- Token introspection/validation
- User login/logout
- Token exchange
- Realm operations

---

## OpenTelemetry Collector (Sidecar / DaemonSet)

### Deployment (DaemonSet en EKS)
```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: otel-collector
  namespace: observability
spec:
  template:
    spec:
      containers:
      - name: otel-collector
        image: otel/opentelemetry-collector-contrib:0.106
        args: ["--config=/etc/otel/config.yaml"]
        volumeMounts:
        - name: config
          mountPath: /etc/otel
      volumes:
      - name: config
        configMap:
          name: otel-collector-config
```

### Configuración (ConfigMap)
```yaml
# otel-collector-config.yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318
  prometheus:
    config:
      scrape_configs:
        - job_name: 'stock-price-adapter'
          kubernetes_sd_configs:
          - role: pod
            namespaces:
              names: [andes-retail]
          relabel_configs:
          - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
            action: keep
            regex: true
        - job_name: 'kong'
          kubernetes_sd_configs:
          - role: pod
            namespaces:
              names: [kong]
        - job_name: 'keycloak'
          kubernetes_sd_configs:
          - role: pod
            namespaces:
              names: [keycloak]

processors:
  batch:
    timeout: 10s
    send_batch_size: 1000
  memory_limiter:
    check_interval: 1s
    limit_mib: 512
  resource:
    attributes:
    - key: deployment.environment
      value: "staging"
      action: upsert
    - key: service.namespace
      value: "andes-retail"
      action: upsert

exporters:
  otlp:
    endpoint: "https://xray.us-east-1.amazonaws.com"
    headers:
      "x-amzn-otlp-trace-id": "${AWS_XRAY_TRACE_ID}"
  logging:
    loglevel: debug

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch, resource]
      exporters: [otlp, logging]
    metrics:
      receivers: [otlp, prometheus]
      processors: [memory_limiter, batch, resource]
      exporters: [logging]  # Prometheus scrapea directo
```

---

## AWS X-Ray Integration

### Configuración
- **Region**: `us-east-1`
- **Endpoint**: `https://xray.us-east-1.amazonaws.com`
- **Authentication**: IAM Role para OTel Collector (`AWSXRayDaemonWriteAccess`)

### Service Map X-Ray
- Servicios: `kong-ingress`, `stock-price-adapter`, `keycloak`, `redis`, `sap-erp`
- Latencia por segmento
- Errores por servicio
- Root cause analysis

---

## Sampling Strategy

| Tipo | Probabilidad | Justificación |
|---|---|---|
| **Error spans** | 100% | Debugging crítico |
| **Success spans** | 10% | Costo/beneficio (alto volumen) |
| **Slow requests (>300ms)** | 50% | Investigar latencia |
| **Stale-cache / Fail-closed** | 100% | Degradación crítica |

### Implementación (Spring Boot)
```java
@Bean
Sampler sampler() {
    return Sampler.alwaysSample()  // Para errors (manejado por bridge)
        .orElse(Sampler.traceIdRatioBased(0.1))  // 10% success
        .orElse(Sampler.traceIdRatioBased(0.5).and(Sampler.spanAttribute("latencyMs", > 300))); // 50% slow
}
```

---

## Exemplars (Prometheus + Traces)

### Configuración
```yaml
management:
  prometheus:
    metrics:
      export:
        exemplars:
          enabled: true
```
- **Ejemplar**: Trace ID adjunto a métrica (ej. latencia p95)
- **Uso**: Click en gráfico Prometheus → salta a trace en X-Ray/Jaeger

---

## Correlación Logs ↔ Traces ↔ Metrics

| Señal | Correlation ID | Uso |
|---|---|---|
| **Logs** | `correlationId` (MDC/JSON) | CloudWatch Logs Insights |
| **Traces** | `traceId` (W3C traceparent) | X-Ray / Jaeger |
| **Metrics** | `exemplar.traceId` | Prometheus exemplars → Trace |
| **Correlation ID** | `X-Correlation-ID` (UUID) | Legacy, cross-system |

### Mapping
| Log Field | Trace Field | Metric Exemplar |
|---|---|---|
| `correlationId` (UUID) | `traceId` (W3C) | `exemplar.traceId` |
| `spanId` | `spanId` | `exemplar.spanId` |

---

## Costos Estimados (AWS X-Ray)

| Componente | Costo mensual estimado (staging) |
|---|---|
| **Traces ingestados** (10% sampling, ~100K req/día) | ~$5-10/mes |
| **X-Ray Service Map** | Incluido |
| **Trace retention (30 días)** | ~$1-2/mes |
| **OTel Collector (DaemonSet)** | Costo EKS nodes (ya existente) |
| **Total estimado** | **$6-12/mes por entorno** |

---

## Verificación de Trazabilidad

| Check | Comando | Esperado |
|---|---|---|
| Trace generado | `curl -H "X-Correlation-ID: test-123" ... | grep traceparent` | Header `traceparent` en response |
| Span generado | X-Ray Console → Service Map | Servicios visibles |
| Exemplar en métrica | `curl /actuator/prometheus | grep exemplar` | `traceId` en métricas |
| Logs correlacionados | `kubectl logs ... | jq 'select(.correlationId=="...")'` | Logs con mismo correlationId |
| Propagación Kong | `kubectl logs -n kong ... | grep traceparent` | Trace propagado |

---

## Costos vs Beneficio (Decisión)

| Opción | Costo | Cobertura | Decisión |
|---|---|---|---|
| **Sin tracing** | $0 | 0% | ❌ No cumple NFR-08 |
| **Solo logs + correlation-id** | $0 | Logs only | ❌ Insuficiente para root cause |
| **OpenTelemetry + X-Ray (10% sampling)** | ~$10/mes | 100% errors, 10% success | ✅ **Seleccionado** |
| **100% sampling** | ~$100/mes | 100% todo | ❌ Costo excesivo |

---

## Próximos Pasos

1. Añadir dependencias OpenTelemetry a `build.gradle.kts`
2. Configurar `application.yml` con OTLP endpoint
3. Desplegar OTel Collector DaemonSet via Helm/ArgoCD
4. Configurar Kong plugin OpenTelemetry
5. Configurar Keycloak OTel env vars
6. Validar en staging: Service Map X-Ray, exemplars en Prometheus
7. Ajustar sampling rates basado en volumen real