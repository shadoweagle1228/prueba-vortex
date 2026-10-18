## Sources

- [scalability-requirements.md] Requerimientos de escalabilidad y picos 10x.
- [NFR-03, NFR-11] Picos 10x y Rate Limiting.
- [CA-02] Backpressure hacia legados.

---

# Diseño de Escalabilidad — `skeleton-stock-query`

Este documento detalla la arquitectura de escalabilidad horizontal, políticas de auto-escalado en EKS y rate limiting perimetral.

---

## 1. Configuración de Horizontal Pod Autoscaler (HPA v2)

```yaml
# Ilustrativo: Manifiesto HPA para stock-price-adapter
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: stock-price-adapter-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: stock-price-adapter
  minReplicas: 2
  maxReplicas: 10
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
```

---

## 2. Topología de Pods y Anti-Afinidad en EKS

Para garantizar que los pods se distribuyan de manera uniforme entre las zonas de disponibilidad de AWS y no colapsen en un solo nodo EC2:

```yaml
# Ilustrativo: Regla de anti-afinidad y distribución por AZ
topologySpreadConstraints:
  - maxSkew: 1
    topologyKey: "topology.kubernetes.io/zone"
    whenUnsatisfiable: DoNotSchedule
    labelSelector:
      matchLabels:
        app: stock-price-adapter
```

---

## 3. Integración de Rate Limiting en Kong Ingress

Se implementa el plugin `rate-limiting` de Kong a nivel de `KongConsumer` asociado al `client_id` del canal:
- **Límite Nominal:** 100 req/s por consumidor.
- **Ráfaga Permitida (Burst):** Hasta 150 req/s por 10 segundos.
- **Respuesta de Exceso:** Código HTTP 429 con cabecera `Retry-After: 1` y cuerpo en formato RFC 7807 (`ProblemDetails`).

---

## Assumptions & Open Questions

- [assumption] Kong Ingress Controller utiliza Redis como backend compartido para contadores de rate limiting, permitiendo precisión global a través de todas las réplicas del Ingress.
