package cl.sanmarcos.vetsm.comun.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Datos generales que muestra Swagger UI. Cada microservicio publica su propia documentación
 * (título y descripción en app.documentacion.*) y el gateway las junta en una sola página:
 * http://localhost:8080/swagger-ui.html, con un selector para elegir el servicio.
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_JWT = "bearerAuth";

    @Bean
    public OpenAPI documentacionApi(@Value("${app.documentacion.titulo}") String titulo,
            @Value("${app.documentacion.descripcion}") String descripcion) {
        return new OpenAPI()
                .info(new Info()
                        .title(titulo)
                        .version("v1")
                        .description(descripcion + """


                                - Todas las rutas están versionadas bajo `/api/v1`.
                                - Las respuestas son JSON. Los errores siempre tienen el mismo formato (`ErrorResponse`).
                                - Los precios están en pesos chilenos (CLP), sin decimales.

                                **Cómo probar los endpoints protegidos:** en el servicio **ms-usuarios** ejecuta
                                `POST /api/v1/auth/login` con una cuenta de demostración, copia el `accessToken`,
                                presiona **Authorize** y pégalo. El mismo token sirve en todos los microservicios.
                                Dura 15 minutos. Cada endpoint indica qué rol lo puede usar.
                                """)
                        .contact(new Contact().name("Equipo Veterinaria San Marcos")))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token de acceso entregado por POST /api/v1/auth/login (ms-usuarios)")))
                // Por defecto todos los endpoints piden el token; los públicos lo quitan con @SecurityRequirements.
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }
}
