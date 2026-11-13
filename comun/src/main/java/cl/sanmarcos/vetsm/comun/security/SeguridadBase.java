package cl.sanmarcos.vetsm.comun.security;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Seguridad común a todos los microservicios. Funciona en dos capas:
 *
 * 1. Por ruta: cada servicio indica sus endpoints públicos (método {@link #construir}); todo lo demás
 *    exige un token JWT válido.
 * 2. Por rol: cada endpoint tiene @PreAuthorize (activado aquí con @EnableMethodSecurity).
 *
 * Ningún servicio guarda sesiones (STATELESS): cada petición trae su token en la cabecera
 * "Authorization: Bearer ...". Por eso cualquier servicio puede verificar a un usuario sin
 * preguntarle a ms-usuarios.
 */
@Configuration
@EnableMethodSecurity
public class SeguridadBase {

    /** Rol de los tokens que usan los microservicios para llamarse entre sí (ver TokenDeServicio). */
    public static final String ROL_SERVICIO = "SERVICIO";

    private final RespuestasDeSeguridad respuestas;
    private final JwtAuthenticationConverter convertidorJwt;

    public SeguridadBase(RespuestasDeSeguridad respuestas, JwtAuthenticationConverter convertidorJwt) {
        this.respuestas = respuestas;
        this.convertidorJwt = convertidorJwt;
    }

    /** Arma la cadena de seguridad con las rutas públicas propias de cada servicio. */
    public SecurityFilterChain construir(HttpSecurity http,
            Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry> rutasPublicas)
            throws Exception {
        http
                // Con tokens en la cabecera no hay riesgo de CSRF; la cookie de renovación usa SameSite=Strict.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // La consola de H2 (solo en el perfil h2) se muestra dentro de un iframe.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(rutas -> {
                    // Documentación y consola de desarrollo
                    rutas.requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/h2-console/**", "/error").permitAll();
                    rutasPublicas.customize(rutas);
                    // Todo lo demás exige un token válido; el rol lo revisa @PreAuthorize en cada endpoint.
                    rutas.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(convertidorJwt))
                        .authenticationEntryPoint(respuestas))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(respuestas)
                        .accessDeniedHandler(respuestas));
        return http.build();
    }

    /**
     * CORS: qué sitios web pueden llamar a la API desde el navegador. Normalmente el navegador llama
     * al gateway (mismo origen), pero se deja configurado para llamadas directas en desarrollo.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.origenes}") String origenes) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(origenes.split(",")).map(String::trim).filter(o -> !o.isEmpty()).toList());
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setExposedHeaders(List.of("Location"));
        cors.setAllowCredentials(true);
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource fuente = new UrlBasedCorsConfigurationSource();
        fuente.registerCorsConfiguration("/api/**", cors);
        return fuente;
    }
}
