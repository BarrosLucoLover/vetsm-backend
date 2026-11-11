package cl.sanmarcos.vetsm.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Datos generales que muestra Swagger UI (/swagger-ui.html). Cada endpoint se documenta en su controlador. */
@Configuration
public class OpenApiConfig {

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
                                """)
                        .contact(new Contact().name("Equipo Veterinaria San Marcos")))
                .tags(List.of(
                        new Tag().name("Categorías").description("Categorías del catálogo de la tienda"),
                        new Tag().name("Productos").description("Servicios de la clínica y artículos de la tienda"),
                        new Tag().name("Usuarios").description("Cuentas del personal y de los clientes"),
                        new Tag().name("Mascotas").description("Pacientes de la clínica"),
                        new Tag().name("Citas").description("Agenda de atenciones"),
                        new Tag().name("Boletas").description("Compras de la tienda y su pago"),
                        new Tag().name("Reportes").description("Métricas para el panel de administración")));
    }
}
