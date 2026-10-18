# Instrucciones de Pruebas de Seguridad — `skeleton-stock-query`

## Objetivos de seguridad (NFR/CA)

| ID | Requisito |
|---|---|
| NFR-06 | Cifrado TLS 1.3 + KMS (datos en tránsito y reposo) |
| NFR-08 | Log sanitization / correlation-id (no PII en logs) |
| NFR-11 | Rate limiting por canal (Kong) |
| CA-01 | PCI-DSS base (CDE, tokenización, enmascaramiento PII) |
| CA-02 | Backpressure en adaptadores legacy |
| CA-03 | Clean Architecture (Dependency Rule) |

## Pruebas automatizadas en CI (ejecutadas en `test`)

### 1. Validación de contrato de seguridad (C-01)
- **Archivo**: `StockControllerTest`, `GlobalExceptionHandlerTest`
- **Qué verifica**:
  - Endpoint requiere `Authorization: Bearer <JWT>` → 401 sin token
  - Scope `SCOPE_inventory:read` obligatorio → 403 sin scope
  - Respuestas 401/403/503 en `application/problem+json` (RFC 7807)
  - `type` = URN `urn:andes:error:*`, `instance`, `correlationId`, `timestamp`
  - 503 incluye `Retry-After: 5`
  - Cabecera `X-Stock-Source: cache|erp|stale-cache` presente

### 2. Propagación de correlation-id
- **Archivo**: `CorrelationIdFilterTest`
- **Qué verifica**:
  - Cabecera entrante `X-Correlation-ID` propagada a respuesta
  - Generación automática (UUID v4) si no viene
  - Longitud máx. 128 chars (trunca si excede)
  - Limpieza MDC tras request
  - `correlationId` en problem+json nunca vacío

### 3. Arquitectura limpia (CA-03)
- **Archivo**: `ArchitectureTest` (ArchUnit)
- **Qué verifica**:
  - `domain` no depende de `application`, `infrastructure`, `web`
  - `application` no depende de `infrastructure`, `web`
  - `infrastructure` no depende de `web`

### 4. Configuración de resiliencia (CA-02, NFR-10, NFR-12)
- **Archivo**: `StockPriceAdapterApplicationTests.la resiliencia del ERP toma los parámetros del diseño`
- **Qué verifica**:
  - CircuitBreaker: ventana 30, wait 60s, slow-call 250ms/50%
  - Bulkhead: máx. 20 concurrentes, wait 100ms
  - Timeout SAP: 250ms lectura, 100ms conexión, pool 20

## Pruebas de seguridad NO automatizadas en CI (requieren entorno desplegado)

### 1. Validación TLS 1.3 + KMS (NFR-06)
```bash
# Verificar que endpoints solo aceptan TLS 1.3
nmap --script ssl-enum-ciphers -p 443 api.andes-retail.example

# Verificar que Redis/Aurora usan cifrado en tránsito (KMS)
aws elasticache describe-replication-groups --replication-group-id andes-retail-redis \
  --query 'ReplicationGroups[0].TransitEncryptionEnabled'
aws rds describe-db-clusters --db-cluster-identifier andes-retail-aurora \
  --query 'DBClusters[0].StorageEncrypted'
```

### 2. Rate limiting Kong (NFR-11)
```bash
# 101 requests en < 1s desde mismo consumer → 429
for i in {1..101}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -H "Authorization: Bearer $TOKEN" \
    https://api.andes-retail.example/v1/inventory/stock/SKU-1
done | sort | uniq -c
# Debe mostrar: 100 x 200, 1 x 429
```

### 3. Sanitización de logs (NFR-08)
```bash
# Verificar que no hay PII en logs JSON
kubectl logs -n andes-retail -l app.kubernetes.io/name=stock-price-adapter \
  | jq -r '.message' | grep -E '(password|token|secret|credit|card|pan)' && echo "FALLO: PII detectado" || echo "OK"
# Verificar correlation-id presente
kubectl logs ... | jq -r '.correlationId' | grep -E '^[0-9a-f-]{36}$'
```

### 4. PCI-DSS: CDE isolation (CA-01)
```bash
# Verificar subredes aisladas para Redis/Aurora
aws ec2 describe-subnets --filters "Name=tag:Name,Values=*cde*" \
  --query 'Subnets[*].{ID:SubnetId,AZ:AvailabilityZone,RouteTable:RouteTableId}'

# Verificar que no hay ruta a Internet Gateway desde CDE
aws ec2 describe-route-tables --route-table-ids <rtb-cde> \
  --query 'RouteTables[0].Routes[?GatewayId!=null].GatewayId'
```

### 5. Validación JWKS / OIDC (FR-11)
```bash
# Verificar que JWKS es alcanzable desde pod
kubectl exec -n andes-retail deploy/stock-price-adapter -- \
  curl -s -o /dev/null -w "%{http_code}" \
  https://auth.andes-retail.example/realms/andes-retail/protocol/openid-connect/certs
# Debe ser 200

# Verificar token válido es aceptado, inválido rechazado
TOKEN_VALID=$(curl -s -X POST ... client_credentials ... | jq -r .access_token)
curl -H "Authorization: Bearer $TOKEN_VALID" https://api.../v1/inventory/stock/SKU-1
# 200

TOKEN_INVALID="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.invalid.signature"
curl -H "Authorization: Bearer $TOKEN_INVALID" https://api.../v1/inventory/stock/SKU-1
# 401
```

## Herramientas SAST/DAST recomendadas

| Herramienta | Uso |
|---|---|
| **SpotBugs + FindSecBugs** | SAST Java/Kotlin (incluido en Gradle opcional) |
| **OWASP Dependency Check** | Vulnerabilidades en dependencias (`./gradlew dependencyCheckAnalyze`) |
| **Trivy** | Escaneo imagen contenedor (`trivy image stock-price-adapter:latest`) |
| **k6 + OWASP ZAP** | DAST básico en staging |

## Gates de seguridad en CI

```yaml
# .github/workflows/ci.yml (extracto)
- name: Dependency Check
  run: ./gradlew dependencyCheckAnalyze --no-daemon

- name: Container Scan
  uses: aquasecurity/trivy-action@master
  with:
    image-ref: 'stock-price-adapter:latest'
    severity: 'CRITICAL,HIGH'
```

## No verificado en CI local

- TLS 1.3 end-to-end (requiere certs reales en EKS)
- Rate limiting Kong con consumer real
- Failover de Redis/Aurora bajo ataque
- Escaneo SAST/DAST completo (solo dependency-check básico)