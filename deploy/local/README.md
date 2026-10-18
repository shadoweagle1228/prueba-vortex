# Entorno local (walking skeleton)

Levanta el emisor OIDC real y la caché para poder ejecutar el slice
`GET /v1/inventory/stock/{sku}` completo, con JWT firmado y rate limiting
aplicado por la aplicación.

```bash
docker compose -f deploy/local/docker-compose.yml up -d
```

| Servicio | Puerto | Qué aporta |
|---|---|---|
| Keycloak 24 | 8081 | Realm `andes-retail` importado: clientes `canal-ecommerce`, `canal-tienda`, `operador-cli`; client scopes `inventory:read` y `metrics:read` (FR-11, US-08). |
| Redis 7 | 6379 | Caché `stock:fresh:*` / `stock:stale:*` (BR-STK-01, BR-STK-03). |

## Obtener un token y llamar al endpoint

```bash
TOKEN=$(curl -s -X POST \
  "http://localhost:8081/realms/andes-retail/protocol/openid-connect/token" \
  -d "grant_type=client_credentials" \
  -d "client_id=canal-ecommerce" \
  -d "client_secret=canal-ecommerce-secret" \
  | python -c "import json,sys; print(json.load(sys.stdin)['access_token'])")

curl -i -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/v1/inventory/stock/ABC-123
```

La respuesta trae la cabecera `X-Stock-Source: cache|erp|stale-cache` y el
cuerpo de C-01 (`available`, `reserved`, `lastUpdated`, `cacheHit`).

## Comprobar el comportamiento

| Escenario | Cómo provocarlo | Resultado esperado |
|---|---|---|
| Acierto de caché | Llamar dos veces seguidas | `X-Stock-Source: cache`, `cacheHit: true` |
| Degradación a obsoleto | Parar SAP (`docker compose stop` del mock) y volver a llamar tras expirar el TTL fresco (60 s) | `X-Stock-Source: stale-cache`, `cacheHit: true` |
| Fail-closed | Parar SAP y borrar la clave obsoleta (`redis-cli del stock:stale:ABC-123:W01`) | `503` con `Retry-After: 5` y `type: urn:andes:error:stock-unavailable` |
| Sin scope | Pedir el token con `operador-cli` y llamar a un endpoint con `metrics:read` | `403` con `application/problem+json` |

## Sondas y métricas

```bash
curl -s http://localhost:8080/actuator/health/readiness   # público
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/actuator/prometheus  # 401 sin bearer
```
