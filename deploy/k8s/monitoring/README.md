# Monitoreo del adaptador de stock

`servicemonitor.yaml` describe el scrape de `/actuator/prometheus` y **no** está
incluido en `deploy/k8s/kustomization.yaml` a propósito: requiere que el
Prometheus Operator (CRDs `monitoring.coreos.com/v1`) esté instalado en el
clúster.

```bash
# Con el Prometheus Operator presente:
kubectl apply -k deploy/k8s/monitoring
```

## Por qué hace falta un bearer

El endpoint de métricas está detrás del resource server OAuth2 igual que el
endpoint de negocio: la política es *fail-closed* (CA-01), así que no se expone
ninguna superficie sin autenticar. El scrape se autentica con un token emitido
por Keycloak que lleva el scope `metrics:read`:

1. En Keycloak, crear un *service account* (cliente confidencial) con el client
   scope `metrics:read` asignado como default.
2. Guardar su token en AWS Secrets Manager; External Secrets Operator lo
   sincroniza en el Secret `stock-price-adapter-metrics-token` (clave `token`).
3. `ServiceMonitor` lo envía en `Authorization: Bearer <token>`.

## Red

`deploy/k8s/networkpolicy.yaml` sólo permite ingress al puerto `http` desde los
namespaces `kong` (tráfico de negocio) y `monitoring` (scrape). Cualquier otro
origen se descarta antes de llegar a la aplicación.

## Alertas sugeridas

| Alerta | Condición | Requerimiento |
|---|---|---|
| `StockAdapterHighLatency` | p95 de `http_server_requests_seconds` > 300 ms en 5 min | NFR-01 |
| `SapCircuitBreakerOpen` | `resilience4j_circuitbreaker_state{state="open"} == 1` | NFR-04, NFR-10 |
| `StockServedFromStaleCache` | tasa de `X-Stock-Source=stale-cache` > 5% en 5 min | BR-STK-03 |
| `CacheMissRate` | `cacheHit=false` sostenido por encima del presupuesto | NFR-01 |
