package cl.sanmarcos.vetsm.usuarios.config;

import cl.sanmarcos.vetsm.comun.security.SeguridadBase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Rutas públicas de ms-usuarios: las de la sesión. Todo lo demás exige token (ver SeguridadBase). */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, SeguridadBase base) throws Exception {
        return base.construir(http, rutas -> rutas
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/registro",
                        "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll());
    }
}
