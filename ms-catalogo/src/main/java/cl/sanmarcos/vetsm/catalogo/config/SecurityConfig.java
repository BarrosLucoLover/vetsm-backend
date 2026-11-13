package cl.sanmarcos.vetsm.catalogo.config;

import cl.sanmarcos.vetsm.comun.security.SeguridadBase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Rutas públicas de ms-catalogo: ver el catálogo de la tienda. Todo lo demás exige token. */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, SeguridadBase base) throws Exception {
        return base.construir(http, rutas -> rutas
                .requestMatchers(HttpMethod.GET, "/api/v1/productos/**", "/api/v1/categorias/**").permitAll());
    }
}
