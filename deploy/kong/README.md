# Rate limiting perimetral en Kong (NFR-11)

Kong Ingress Controller se aprovisiona con CDK (`AndesRetailComputeStack` →
release Helm `kong` en el namespace `kong`, 2 réplicas). Estos manifiestos son
el contrato de rate limiting que se aplica sobre él.

```bash
kubectl apply -k deploy/kong
```

| Recurso | Qué hace | Requerimiento |
|---|---|---|
| `KongPlugin/stock-rate-limit` | 100 req/s **por consumer** (`limit_by: consumer`), contadores compartidos en ElastiCache Redis. | NFR-11 |
| `KongClusterPlugin/stock-rate-limit-anonymous` | 20 req/s por IP para peticiones sin consumer identificable. | NFR-11, CA-02 |
| `KongPlugin/stock-jwt` | Valida firma y `exp` del JWT y asocia la petición al consumer por el claim `iss`. | FR-11 |
| `Ingress/stock-price-adapter` | Publica `/v1/inventory` en `api.andes-retail.example` con ambos plugins. | FR-01 |
| `KongConsumer` × canal | Un consumer por canal externo, con su credencial JWT. | NFR-11 |

## Comportamiento al exceder el límite

Kong responde `429 Too Many Requests`. El plugin `rate-limiting` no emite cuerpo
RFC 7807, así que el mensaje de exceso lo produce Kong (`{"message":"API rate
limit exceeded"}`) y la cabecera `Retry-After` la añade el propio plugin. Si el
consumidor necesita el cuerpo `application/problem+json` completo, se añade un
plugin `response-transformer` con ese `type`; se dejó fuera del walking skeleton
para no ampliar la superficie configurada.

## Contadores globales

`policy: redis` es lo que hace que el límite sea global a través de las réplicas
del Ingress (sin él cada réplica contaría por separado). `fault_tolerant: true`
evita que un Redis caído bloquee el tráfico: si los contadores no se pueden
incrementar, la petición pasa en lugar de fallar.
