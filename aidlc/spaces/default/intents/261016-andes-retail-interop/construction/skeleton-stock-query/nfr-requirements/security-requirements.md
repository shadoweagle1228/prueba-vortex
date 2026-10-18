## Sources

- [desc] Initial description del proyecto Andes Retail.
- [CA-01] Restricción absoluta PCI-DSS, segmentación CDE y enmascaramiento PII.
- [FR-11] Autenticación OAuth2/OIDC multi-tenant.
- [NFR-05] Enmascaramiento PII en logs.
- [NFR-06] Cifrado TLS 1.3 y KMS.
- [NFR-07] Scopes y multi-tenant.
- [NFR-09] Rotación de secretos.

---

# Requerimientos de Seguridad y Cumplimiento — `skeleton-stock-query`

Este documento define los controles mandatorios de seguridad, autenticación, protección de datos y cumplimiento regulatorio para la Unidad 1.

---

## 1. Autenticación y Autorización (Zero-Trust & OAuth2)

- **Mecanismo de Autenticación:** Tokens Bearer JWT conformes a OIDC emitidos por Keycloak OSS [FR-11].
- **Firma Criptográfica:** Algoritmo RS256 verificado con la clave pública de Keycloak (descargada vía endpoint JWKS con caché local).
- **Control de Acceso Basado en Scopes:**
  - El endpoint `GET /v1/inventory/stock/{sku}` exige estrictamente el scope `inventory:read` en el claim `scope` del JWT [NFR-07].
  - Peticiones sin token retornan `401 Unauthorized` (RFC 7807).
  - Peticiones con token válido pero sin scope `inventory:read` retornan `403 Forbidden`.
- **Aislamiento Multi-Tenant:**
  - El claim `tenantId` (e.g. `ECOMMERCE`, `MARKETPLACE_A`) se valida contra los almacenes permitidos para dicho canal.

---

## 2. Protección de Datos y Cifrado

- **Cifrado en Tránsito:**
  - TLS 1.3 mandatorio en toda comunicación externa e inter-servicio [NFR-06].
  - Cipher suites autorizadas: `TLS_AES_128_GCM_SHA256` y `TLS_AES_256_GCM_SHA384`.
- **Cifrado en Reposo:**
  - Datos en ElastiCache Redis cifrados en reposo mediante llaves gestionadas por AWS KMS [NFR-06].
  - Transit Encryption habilitado en la conexión pod-Redis con autenticación AUTH token de Redis.
- **Enmascaramiento y Sanitización de Logs [CA-01, NFR-05]:**
  - Ningún dato PII ni información sensible viaja en los payloads de stock.
  - Prohibición estricta de registrar credenciales, tokens JWT completos o cadenas de conexión en logs estructurados.

---

## 3. Gestión y Rotación de Secretos [NFR-09]

- Las credenciales de acceso al ERP SAP y el token AUTH de Redis se gestionan exclusivamente en **AWS Secrets Manager**.
- Inyección mediante **External Secrets Operator** de Kubernetes como `Secret` de K8s montado en variables de entorno en memoria de solo lectura.
- Política de rotación automática cada 90 días para credenciales de integración de sistemas legacy.

---

## 4. Criterios de Aceptación y Verificación

| Requerimiento | Método de Verificación | Resultado Esperado |
|---|---|---|
| Scope inválido | Prueba automatizada con JWT sin `inventory:read` | Respuesta `403 Forbidden` |
| Token expirado | Prueba automatizada con JWT `exp` en el pasado | Respuesta `401 Unauthorized` |
| Cifrado TLS 1.3 | Escaneo `nmap --script ssl-enum-ciphers` | Solo suites TLS 1.3 aceptadas |
| Scan de Secretos | Escaneo pre-commit con GitGuardian / Trufflehog | Cero secretos en repositorio |

---

## Assumptions & Open Questions

- [assumption] Keycloak se ejecuta dentro del cluster EKS en un namespace dedicado `identity` con réplicas de alta disponibilidad conectadas a Aurora PostgreSQL.
