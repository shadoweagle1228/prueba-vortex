# CI Configuration — `skeleton-stock-query`

## Herramienta CI

**GitHub Actions** (`.github/workflows/ci.yml`)

## Pipeline actual

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: read

jobs:
  build-and-test:
    name: Build, lint and test
    runs-on: ubuntu-latest
    timeout-minutes: 30
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v3
      - name: Verify Gradle wrapper
        uses: gradle/actions/wrapper-validation@v3
      - name: Lint (ktlint)
        run: ./gradlew ktlintCheck --no-daemon
      - name: Test
        run: ./gradlew test --no-daemon
      - name: Coverage gate (>= 80%)
        run: ./gradlew jacocoTestCoverageVerification --no-daemon
      - name: Assemble
        run: ./gradlew assemble --no-daemon
      - name: Upload coverage report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: jacoco-report
          path: "**/build/reports/jacoco/**"
          if-no-files-found: ignore
          retention-days: 7
      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results
          path: "**/build/test-results/test/*.xml"
          if-no-files-found: ignore
          retention-days: 7

  docker:
    name: Build container image
    runs-on: ubuntu-latest
    needs: build-and-test
    timeout-minutes: 30
    steps:
      - name: Checkout
        uses: actions/checkout@v4
      - name: Set up Buildx
        uses: docker/setup-buildx-action@v3
      - name: Build (linux/arm64)
        uses: docker/build-push-action@v5
        with:
          context: .
          file: services/stock-price-adapter/Dockerfile
          platforms: linux/arm64
          push: false
```

## Estrategia de ramas

- **main**: rama protegida, merges solo vía PR
- **feature/***: ramas de desarrollo, PR hacia main
- **release/***: ramas de release (opcional, para versionado)

## Gates de calidad (en PR y push a main)

| Gate | Comando | Umbral | Bloqueante |
|---|---|---|---|
| Lint | `./gradlew ktlintCheck` | 0 violaciones | Sí |
| Tests | `./gradlew test` | 0 fallos | Sí |
| Cobertura líneas | `./gradlew jacocoTestCoverageVerification` | ≥ 80 % | Sí |
| Ensamblado | `./gradlew assemble` | JAR generado | Sí |
| Imagen ARM64 | `docker buildx build --platform linux/arm64` | Build exitoso | Sí |

## Artefactos publicados

| Artefacto | Ubicación | Retención |
|---|---|---|
| Reporte JaCoCo | `build/reports/jacoco/**` | 7 días |
| **Resultados tests (JUnit XML)** | `build/test-results/test/*.xml` | 7 días |
| Imagen Docker (local) | No publicado en CI (solo build) | — |

> **Referencia upstream**: `build-test-results` consumido desde `construction/build-and-test/test-results.md`

## Entorno de ejecución CI

- **Runner**: `ubuntu-latest` (GitHub hosted)
- **JDK**: Temurin 21 (setup-java@v4)
- **Gradle**: Wrapper 8.7 (gradle/actions/setup-gradle@v3 + wrapper-validation@v3)
- **Docker**: Buildx (docker/setup-buildx-action@v3 + build-push-action@v5)
- **Cache**: Gradle cache vía `gradle/actions/setup-gradle@v3`

## Variables de entorno CI

No se requieren secrets para el pipeline actual (build y test no necesitan credenciales AWS). El `cdk deploy` se ejecuta manualmente desde entorno autorizado.

## Extensiones futuras

| Gate | Qué añadir | Cuándo |
|---|---|---|
| Dependency Check | `./gradlew dependencyCheckAnalyze` | Cuando se añadan dependencias críticas |
| Container Scan | Trivy en job `docker` | Antes de primer deploy a staging |
| SAST | SpotBugs + FindSecBugs | Sprint de hardening |
| Contract Test | Pact / Spring Cloud Contract | Cuando haya consumers externos |