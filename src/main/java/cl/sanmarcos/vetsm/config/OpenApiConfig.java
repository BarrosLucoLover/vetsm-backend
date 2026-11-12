package cl.sanmarcos.vetsm.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Datos generales que muestra Swagger UI (/swagger-ui.html). Cada endpoint se documenta en su controlador. */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA_JWT = "bearerAuth";

    @Bean
    public OpenAPI documentacionApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Veterinaria San Marcos")
                        .version("v1")
                        .description("""
                                API REST del sistema de la Clínica Veterinaria San Marcos (DSY1104 Desarrollo Fullstack II).

                                - Todas las rutas están versionadas bajo `/api/v1`.
                                - Las respuestas son JSON. Los errores siempre tienen el mismo formato (`ErrorResponse`).
                                - Los precios están en pesos chilenos (CLP), sin decimales.
                                - El pago con tarjeta es **simulado**: no se conecta a ningún banco.

                                **Cómo probar los endpoints protegidos:** ejecuta `POST /api/v1/auth/login` con una cuenta
                                de demostración, copia el `accessToken` de la respuesta, presiona **Authorize** (arriba a la
                                derecha) y pégalo. El token dura 15 minutos. Cada endpoint indica qué rol lo puede usar.
                                """)
                        .contact(new Contact().name("Equipo Veterinaria San Marcos")))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Token de acceso entregado por POST /api/v1/auth/login")))
                // Por defecto todos los endpoints piden el token; los públicos lo quitan con @SecurityRequirements.
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT))
                .tags(List.of(
                        new Tag().name("Autenticación").description("Inicio y cierre de sesión con JWT"),
                        new Tag().name("Mi cuenta").description("Perfil y tarjetas del usuario que inició sesión"),
                        new Tag().name("Categorías").description("Categorías del catálogo de la tienda"),
                        new Tag().name("Productos").description("Servicios de la clínica y artículos de la tienda"),
                        new Tag().name("Usuarios").description("Cuentas del personal y de los clientes"),
                        new Tag().name("Mascotas").description("Pacientes de la clínica"),
                        new Tag().name("Citas").description("Agenda de atenciones"),
                        new Tag().name("Boletas").description("Compras de la tienda y su pago"),
                        new Tag().name("Reportes").description("Métricas para el panel de administración")));
    }
}
