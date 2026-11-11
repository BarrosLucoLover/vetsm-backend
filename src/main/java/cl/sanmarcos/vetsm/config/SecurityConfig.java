package cl.sanmarcos.vetsm.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * CONFIGURACIÓN TEMPORAL DE LA FASE 2: deja la API abierta para poder construir y probar los
 * endpoints. En la fase 3 se reemplaza por autenticación con JWT y permisos por rol.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                // La consola de H2 (solo en el perfil h2) se muestra dentro de un iframe.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(rutas -> rutas.anyRequest().permitAll());
        return http.build();
    }
}
