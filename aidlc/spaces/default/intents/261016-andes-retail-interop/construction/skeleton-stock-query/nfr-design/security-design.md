## Sources

- [security-requirements.md] Requerimientos de seguridad, OAuth2 JWT y KMS.
- [CA-01] Restricción absoluta PCI-DSS y enmascaramiento PII.
- [NFR-07] Scopes y multi-tenant.

---

# Diseño de Seguridad — `skeleton-stock-query`

Este documento describe la arquitectura de seguridad, la cadena de filtros de autenticación/autorización y las políticas de protección perimetral en Spring Boot.

---

## 1. Cadena de Filtros de Seguridad (Security Filter Chain)

```
[Request HTTP]
       │
       ▼
[1. CorrelationIdFilter] ──► Extrae/Genera X-Correlation-ID y setea MDC
       │
       ▼
[2. JwtAuthenticationFilter] ──► Valida firma RS256 contra JWKS de Keycloak
       │
       ▼
[3. ScopeAuthorizationManager] ──► Exige scope 'inventory:read'
       │
       ▼
[4. MultiTenantContextFilter] ──► Setea TenantId en contexto de ejecución
       │
       ▼
[Controlador REST]
```

### Configuración Declarativa en Kotlin (≤15 líneas)

```kotlin
// Ilustrativo (≤15 líneas): Spring Security 6 / Spring Boot 3 Resource Server
@Bean
fun securityFilterChain(http: HttpSecurity): SecurityFilterChain = http
    .csrf { it.disable() }
    .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
    .authorizeHttpRequests { auth ->
        auth.requestMatchers("/actuator/health/**").permitAll()
        auth.requestMatchers("/v1/inventory/stock/**").hasAuthority("SCOPE_inventory:read")
        auth.anyRequest().authenticated()
    }
    .oauth2ResourceServer { oauth -> oauth.jwt(Customizer.withDefaults()) }
    .build()
```

---

## 2. Validación de Claims y Aislamiento de Tenants

El convertidor de autenticación de JWT (`JwtAuthenticationConverter`) extrae:
- `tenantId`: Identificador del canal (e.g. `ECOMMERCE`).
- `scope`: Mapeado a autoridades de Spring Security con prefijo `SCOPE_`.
- Si un canal intenta consultar un SKU o centro logístico no habilitado para su `tenantId`, el controlador rechaza la solicitud arrojando `AccessDeniedException` (transformada a 403 por el `GlobalExceptionHandler`).

---

## 3. Manejo de Secretos en Runtime

- **External Secrets Operator:** Sincroniza las credenciales de SAP y Redis desde AWS Secrets Manager hacia un `Secret` nativo de Kubernetes.
- En el arranque del pod de Spring Boot, las propiedades `spring.data.redis.password` y `sap.erp.password` se inyectan como variables de entorno seguras de solo lectura, impidiendo su almacenamiento en disco o en el código fuente.

---

## Assumptions & Open Questions

- [assumption] Los certificados TLS para Kong e Ingress son gestionados automáticamente por `cert-manager` en Kubernetes utilizando la CA de Andes Retail o Let's Encrypt / ACM.
