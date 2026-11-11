package cl.sanmarcos.vetsm.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RelojConfig {

    /**
     * Reloj con la hora de Chile. Los servidores en la nube (como AWS) suelen estar en UTC: sin esto,
     * de noche una cita de "hoy" se vería como del día siguiente. Además, en las pruebas se puede
     * reemplazar por un reloj fijo.
     */
    @Bean
    public Clock reloj(@Value("${app.zona-horaria:America/Santiago}") String zona) {
        return Clock.system(ZoneId.of(zona));
    }
}
