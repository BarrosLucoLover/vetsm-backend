package cl.sanmarcos.vetsm.config;

import cl.sanmarcos.vetsm.security.RespuestasDeSeguridad;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Seguridad de la API. Funciona en dos capas:
 *
 * 1. Aquí (por ruta): qué endpoints son públicos y cuáles exigen un token JWT válido.
 * 2. En cada controlador (@PreAuthorize): qué ROL puede usar cada endpoint.
 *
 * La API no guarda sesiones en el servidor (STATELESS): cada petición trae su token en la cabecera
 * "Authorization: Bearer ...", y Spring Security verifica su firma y su vencimiento.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, RespuestasDeSeguridad respuestas,
            JwtAuthenticationConverter convertidorJwt) throws Exception {
        http
                // Con tokens en la cabecera no hay riesgo de CSRF; la cookie de renovación usa SameSite=Strict.
                .csrf(csrf -> csrf.disable())
                .cors(cors -> { })
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // La consola de H2 (solo en el perfil h2) se muestra dentro de un iframe.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(rutas -> rutas
                        // Documentación y consola de desarrollo
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/h2-console/**").permitAll()
                        // Sesión: login, registro, renovación y cierre
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/registro",
                                "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        // Catálogo de la tienda
                        .requestMatchers(HttpMethod.GET, "/api/v1/productos/**", "/api/v1/categorias/**").permitAll()
                        // Solicitud de cita desde el formulario público de la portada
                        .requestMatchers(HttpMethod.POST, "/api/v1/citas").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Todo lo demás exige un token válido; el rol lo revisa @PreAuthorize en cada endpoint.
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(convertidorJwt))
                        .authenticationEntryPoint(respuestas))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(respuestas)
                        .accessDeniedHandler(respuestas));
        return http.build();
    }

    /**
     * CORS: qué sitios web pueden llamar a la API desde el navegador. Solo el frontend propio
     * (configurable con app.cors.origenes). allowCredentials permite enviar la cookie de renovación.
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
