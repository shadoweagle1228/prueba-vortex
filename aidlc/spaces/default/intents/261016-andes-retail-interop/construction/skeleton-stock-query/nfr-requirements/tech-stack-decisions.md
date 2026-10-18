## Sources

- [desc] Initial description del proyecto Andes Retail.
- [TF-stack] Stack Kotlin + Spring Boot / Python CDK.
- [team-practices] Prácticas de testing (JUnit 5, Mockito, ktlint, ArchUnit).
- [CA-03] Clean Architecture.

---

# Decisiones de Pila Tecnológica — `skeleton-stock-query`

Este documento consolida las decisiones de lenguaje, frameworks, librerías y herramientas de construcción para el microservicio `stock-price-adapter` y la infraestructura del walking skeleton.

---

## 1. Stack de Backend y Dominio

| Componente | Tecnología Seleccionada | Versión | Justificación Técnica |
|---|---|---|---|
| **Lenguaje de Programación** | Kotlin | 1.9.23+ | Lenguaje tipado seguro con nulos de primer nivel, inline value classes para DDD, inmutabilidad nativa y compatibilidad total con JVM. |
| **Runtime JVM** | Eclipse Temurin JDK (LTS) | 21 | Soporte LTS moderno con optimizaciones para arquitectura ARM64 (AWS Graviton) y Garbage Collector ZGC / G1 de baja latencia. |
| **Framework Base** | Spring Boot | 3.2.4+ | Framework maduro para microservicios cloud-native con Spring Actuator, Spring Security OAuth2 Resource Server y Spring Cache. |
| **Cliente de Caché Redis** | Lettuce (vía Spring Data Redis) | 6.3+ | Cliente asíncrono no bloqueante basado en Netty con soporte nativo de pooling de conexiones y TLS. |
| **Tolerancia a Fallos** | Resilience4j | 2.2.0 | Implementación de Circuit Breaker y Bulkhead ligera, modular y funcional, sin overhead ni hilos huérfanos. |
| **Herramienta de Construcción** | Gradle (Kotlin DSL) | 8.6+ | Scripts de build fuertemente tipados (`build.gradle.kts`) con soporte de multi-proyecto y caché de dependencias. |

---

## 2. Herramientas de Calidad y Pruebas [team.md]

| Categoría | Herramienta | Propósito |
|---|---|---|
| **Framework de Pruebas** | JUnit 5 (Jupiter) | Ejecución de pruebas unitarias y de integración siguiendo ciclo TDD (Red-Green-Refactor). |
| **Aserciones** | AssertJ | Aserciones fluidas y expresivas en pruebas de dominio. |
| **Mocks y Stubs** | MockK / Mockito-Kotlin | Creación de mocks idiomáticos en Kotlin para puertos e interfaces. |
| **Pruebas de Arquitectura** | ArchUnit | Verificación automática de la Dependency Rule de Clean Architecture en CI [CA-03]. |
| **Linter y Formateador** | ktlint | Verificación de estilo de código idiomático Kotlin en pre-commit y CI [team.md]. |
| **Pruebas de Carga** | k6 | Generación de carga HTTP concurrente y validación de percentiles de latencia. |

---

## 3. Infraestructura y Despliegue Cloud

| Componente | Tecnología | Justificación |
|---|---|---|
| **Infraestructura como Código (IaC)** | AWS CDK (Python) | Definición declarativa de VPC, EKS, Redis y Secrets Manager con tipado estricto. |
| **Orquestación de Contenedores** | AWS EKS (Kubernetes 1.29+) | Cluster gestionado sobre instancias EC2 Graviton (ARM64) de bajo costo. |
| **Ingress & API Gateway** | AWS API Gateway + Kong Ingress | Arquitectura híbrida perimetral con rate limiting y balanceo de carga. |
| **Gestión de Identidad (IdP)** | Keycloak OSS | Servidor OIDC/OAuth2 multi-tenant ejecutado en EKS para emisión y validación de JWTs. |

---

## Assumptions & Open Questions

- [assumption] Todas las imágenes de contenedor de Docker se construyen bajo arquitectura multi-arch (`linux/arm64` y `linux/amd64`) para facilitar desarrollo local y despliegue nativo en Graviton.
