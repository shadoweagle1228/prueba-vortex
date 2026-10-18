# Build Instructions — `skeleton-stock-query`

## Entorno y prerrequisitos

- **JDK**: Temurin 21 (configurado vía `jvmToolchain(21)` en `build.gradle.kts`)
- **Gradle**: Wrapper 8.7 (`gradlew` / `gradlew.bat`)
- **Kotlin**: 1.9.x (gestionado por Spring Boot plugin)
- **Python**: 3.11+ para CDK (`infra/requirements.txt`)
- **Node.js**: 20+ (requerido por CDK para síntesis)

## Variables de entorno

| Variable | Descripción | Valor por defecto |
|---|---|---|
| `JAVA_HOME` | Ruta al JDK 21 | Requerida |
| `SAP_BASE_URL` | Endpoint del ERP SAP | `http://localhost:9000` |
| `REDIS_HOST` | Host de Redis | `localhost` |
| `REDIS_PORT` | Puerto de Redis | `6379` |
| `OAUTH_ISSUER_URI` | Emisor OIDC | `http://localhost:8081/realms/andes-retail` |
| `OAUTH_JWKS_URI` | JWKS del emisor | `http://localhost:8081/realms/andes-retail/protocol/openid-connect/certs` |

## Comandos de build

```bash
# 1. Verificar estilo de código (ktlint)
./gradlew ktlintCheck --no-daemon

# 2. Compilar y ejecutar tests con verificación de cobertura
./gradlew test jacocoTestCoverageVerification jacocoTestReport --console=plain --no-daemon

# 3. Ensamblar artefacto ejecutable (JAR fat/bootJar)
./gradlew bootJar --no-daemon

# 4. Sintetizar infraestructura CDK (sin credenciales AWS)
cd infra && py -3 app.py
```

> **Nota Windows/OneDrive**: si Gradle falla con "not a regular file" en `build/`, ejecutar:
> ```bash
> rm -rf services/stock-price-adapter/build
> ./gradlew ... --no-daemon
> ```

## Verificación de build exitoso

- `ktlintCheck`: 0 violaciones (estilo `intellij_idea`, `max_line_length = 140`)
- `test`: **58 tests** en 14 suites, 0 fallos / 0 errores
- `jacocoTestCoverageVerification`: **líneas 95,30 %** (gate ≥ 80 %)
- `py -3 infra/app.py`: EXIT 0, genera `infra/cdk.out/`

## Artefactos producidos

- `services/stock-price-adapter/build/libs/stock-price-adapter-0.1.0.jar`
- `infra/cdk.out/*.template.json` (CloudFormation stacks)
- `build/reports/jacoco/test/jacocoTestReport.xml`
- `build/test-results/test/*.xml`

## Solución de problemas comunes

| Síntoma | Causa | Solución |
|---|---|---|
| `ktlintCheck` falla | Formato de código | Ejecutar `./gradlew ktlintFormat` |
| `compileKotlin` "not a regular file" | OneDrive/Windows lock | `rm -rf services/stock-price-adapter/build` y reintentar con `--no-daemon` |
| `jacocoTestCoverageVerification` falla | Cobertura < 80 % | Añadir tests a componentes descubiertos |
| `py -3 infra/app.py` falla | Dependencias CDK | `pip install -r infra/requirements.txt` en venv limpio |