package com.andesretail.stock.web.security

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain

/**
 * Cadena de seguridad del adaptador de stock.
 *
 * Política *fail-closed* (CA-01): todo lo que no esté explícitamente permitido
 * exige un JWT válido del proveedor de identidad. Las sondas de Kubernetes
 * (`/actuator/health` y sus sub-rutas) sí son públicas porque las ejecuta el kubelet sin
 * credenciales; las métricas de Prometheus quedan detrás del scope
 * `metrics:read` y de la NetworkPolicy de `deploy/k8s`.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    fun problemJsonSecurityHandler(objectMapper: ObjectMapper): ProblemJsonSecurityHandler = ProblemJsonSecurityHandler(objectMapper)

    @Bean
    fun securityFilterChain(http: HttpSecurity, problemJsonSecurityHandler: ProblemJsonSecurityHandler): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                // Sondeas del kubelet: sin credenciales, por eso cubren las subrutas.
                auth.requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                // Métricas: alcanzables por la red de monitoreo con bearer `metrics:read`.
                auth.requestMatchers(HttpMethod.GET, "/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**")
                    .hasAuthority("SCOPE_metrics:read")
                auth.anyRequest().authenticated()
            }
            .oauth2ResourceServer { oauth ->
                oauth.jwt { }
                oauth.authenticationEntryPoint(problemJsonSecurityHandler)
            }
            .exceptionHandling { ex -> ex.accessDeniedHandler(problemJsonSecurityHandler) }
        return http.build()
    }

    @Bean
    fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val converter = JwtGrantedAuthoritiesConverter()
        converter.setAuthorityPrefix("SCOPE_")
        converter.setAuthoritiesClaimName("scope")
        val jwtConverter = JwtAuthenticationConverter()
        jwtConverter.setJwtGrantedAuthoritiesConverter(converter)
        return jwtConverter
    }
}
