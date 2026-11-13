package cl.sanmarcos.vetsm.agenda.config;

import cl.sanmarcos.vetsm.comun.security.SeguridadBase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** Ruta pública de ms-agenda: solicitar una cita desde el formulario de la portada. Todo lo demás exige token. */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http, SeguridadBase base) throws Exception {
        return base.construir(http, rutas -> rutas.requestMatchers(HttpMethod.POST, "/api/v1/citas").permitAll());
    }
}
