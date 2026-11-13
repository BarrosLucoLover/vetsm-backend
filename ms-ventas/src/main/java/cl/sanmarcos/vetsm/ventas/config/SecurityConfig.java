package cl.sanmarcos.vetsm.ventas.config;

import cl.sanmarcos.vetsm.comun.security.SeguridadBase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** ms-ventas no tiene rutas públicas: comprar y ver compras o tarjetas siempre exige token. */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, SeguridadBase base) throws Exception {
        return base.construir(http, rutas -> { });
    }
}
