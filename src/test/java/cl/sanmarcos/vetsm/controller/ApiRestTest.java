package cl.sanmarcos.vetsm.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.sanmarcos.vetsm.model.Producto;
import cl.sanmarcos.vetsm.repository.ProductoRepository;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Pruebas de la API por HTTP: códigos de respuesta, formato JSON, errores y documentación Swagger. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiRestTest extends PruebaConSesion {

    @Autowired private ProductoRepository productos;
    @Autowired private Clock reloj;

    private Producto porNombre(String nombre) {
        return productos.findAll().stream().filter(p -> p.getNombre().equals(nombre)).findFirst().orElseThrow();
    }

    private static String ruta(String location) {
        return location.substring(location.indexOf("/api/v1"));
    }

    @Test
    @DisplayName("GET /productos es público y devuelve el catálogo con los campos que usa el frontend")
    void catalogo() throws Exception {
        mvc.perform(get("/api/v1/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(12)))
                .andExpect(jsonPath("$[?(@.nombre == 'Collar antipulgas')].tipo").value(hasItem("PRODUCTO")))
                .andExpect(jsonPath("$[?(@.nombre == 'Collar antipulgas')].categoriaNombre").value(hasItem("Accesorios")));

        mvc.perform(get("/api/v1/productos").param("tipo", "SERVICIO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)));
    }

    @Test
    @DisplayName("CRUD completo: POST 201 con Location, GET, PUT y DELETE 204")
    void crudCategoria() throws Exception {
        String location = como(ADMIN, post("/api/v1/categorias"), """
                {"nombre": "Farmacia", "descripcion": "Medicamentos veterinarios"}""")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/categorias/")))
                .andExpect(jsonPath("$.nombre").value("Farmacia"))
                .andReturn().getResponse().getHeader("Location");
        String ruta = ruta(location);

        mvc.perform(get(ruta)).andExpect(status().isOk()).andExpect(jsonPath("$.descripcion").value("Medicamentos veterinarios"));
        como(ADMIN, put(ruta), """
                {"nombre": "Farmacia veterinaria"}""").andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Farmacia veterinaria"));
        como(ADMIN, delete(ruta)).andExpect(status().isNoContent());
        mvc.perform(get(ruta)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Datos inválidos responden 400 con un mensaje por campo")
    void validacion() throws Exception {
        como(ADMIN, post("/api/v1/usuarios"), """
                {"nombre": "A", "email": "no-es-correo", "password": "123", "telefono": "123"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.ruta").value("/api/v1/usuarios"))
                .andExpect(jsonPath("$.errores.nombre").exists())
                .andExpect(jsonPath("$.errores.email").value("Ingresa un correo válido."))
                .andExpect(jsonPath("$.errores.password").exists())
                .andExpect(jsonPath("$.errores.telefono").exists())
                .andExpect(jsonPath("$.errores.rol").value("El rol es obligatorio."));
    }

    @Test
    @DisplayName("Un JSON mal formado o un id inválido responden 400; un recurso inexistente, 404")
    void erroresDeFormato() throws Exception {
        como(ADMIN, post("/api/v1/categorias"), "{ esto no es json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("JSON")));
        mvc.perform(get("/api/v1/productos/abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/productos/99999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe el producto 99999."));
    }

    @Test
    @DisplayName("Una regla de negocio incumplida responde 409 con un mensaje claro")
    void conflicto() throws Exception {
        Long categoriaConProductos = porNombre("Consulta General").getCategoria().getId();
        como(ADMIN, delete("/api/v1/categorias/" + categoriaConProductos))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("tiene productos")));
    }

    @Test
    @DisplayName("Las respuestas de usuarios nunca incluyen la contraseña")
    void sinPassword() throws Exception {
        como(ADMIN, get("/api/v1/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(resultado -> {
                    String cuerpo = resultado.getResponse().getContentAsString();
                    if (cuerpo.contains("$2a$") || cuerpo.contains("$2b$")) {
                        throw new AssertionError("La respuesta contiene un hash de contraseña");
                    }
                });
    }

    @Test
    @DisplayName("Flujo de una cita: se solicita sin sesión, la recepción la confirma y una transición inválida responde 409")
    void flujoCita() throws Exception {
        Long servicio = porNombre("Vacunación").getId();
        String fecha = LocalDate.now(reloj).plusDays(9).toString();
        String location = sinSesion(post("/api/v1/citas"), """
                {"nombreMascota": "Luna", "nombreDueno": "Pedro Soto", "telefonoContacto": "+56 9 1122 3344",
                 "servicioId": %d, "fecha": "%s", "hora": "10:30"}""".formatted(servicio, fecha))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.hora").value("10:30"))
                .andExpect(jsonPath("$.fecha").value(fecha))
                .andExpect(jsonPath("$.servicioNombre").value("Vacunación"))
                .andReturn().getResponse().getHeader("Location");
        String ruta = ruta(location);

        como(RECEPCION, patch(ruta + "/estado"), """
                {"estado": "CONFIRMADA"}""").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADA"));
        como(RECEPCION, patch(ruta + "/estado"), """
                {"estado": "PENDIENTE"}""").andExpect(status().isConflict());

        // El mismo horario ya está ocupado.
        sinSesion(post("/api/v1/citas"), """
                {"nombreMascota": "Rocky", "nombreDueno": "María", "telefonoContacto": "912345678",
                 "servicioId": %d, "fecha": "%s", "hora": "10:30"}""".formatted(servicio, fecha))
                .andExpect(status().isConflict());

        como(RECEPCION, get("/api/v1/citas").param("fecha", fecha)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("Compra: 201 con la boleta; la tarjeta rechazada responde 402 y la mal escrita, 400")
    void compra() throws Exception {
        Long collar = porNombre("Collar antipulgas").getId();
        String plantilla = """
                {"items": [{"productoId": %d, "cantidad": 2}],
                 "pago": {"tarjeta": {"numero": "%s", "titular": "Ana García", "vencimiento": "12/35"}, "cvv": "123"}}""";

        como(CLIENTE, post("/api/v1/boletas"), plantilla.formatted(collar, "4111 1111 1111 1111"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clienteEmail").value(CLIENTE))
                .andExpect(jsonPath("$.total").value(25980))
                .andExpect(jsonPath("$.estado").value("PAGADA"))
                .andExpect(jsonPath("$.medioPago.marcaNombre").value("Visa"))
                .andExpect(jsonPath("$.medioPago.ultimos4").value("1111"))
                .andExpect(jsonPath("$.medioPago.codigoAutorizacion").value(matchesPattern("\\d{6}")))
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value(12990))
                .andExpect(resultado -> {
                    if (resultado.getResponse().getContentAsString().contains("4111111111111111")) {
                        throw new AssertionError("La respuesta contiene el número completo de la tarjeta");
                    }
                });

        como(CLIENTE, post("/api/v1/boletas"), plantilla.formatted(collar, "4000 0000 0000 0002"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.mensaje").value(containsString("rechazó")));

        como(CLIENTE, post("/api/v1/boletas"), plantilla.formatted(collar, "4111 1111 1111 1112"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores['tarjeta.numero']").exists());
    }

    @Test
    @DisplayName("El carrito vacío o sin CVV responde 400")
    void compraIncompleta() throws Exception {
        como(CLIENTE, post("/api/v1/boletas"), """
                {"items": [], "pago": {"cvv": ""}}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.items").value("El carrito está vacío."))
                .andExpect(jsonPath("$.errores['pago.cvv']").value("Ingresa el código de seguridad."));
    }

    @Test
    @DisplayName("El reporte resume citas, ventas y stock")
    void reporte() throws Exception {
        como(ADMIN, get("/api/v1/reportes/resumen"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citasPorEstado.PENDIENTE").exists())
                .andExpect(jsonPath("$.citasPorServicio", hasSize(6)))
                .andExpect(jsonPath("$.clientesActivos").value(1));
    }

    @Test
    @DisplayName("Swagger es público, documenta todos los recursos y el esquema de seguridad JWT")
    void swagger() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("API Veterinaria San Marcos"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/perfil']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/tarjetas']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/categorias']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/productos/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/usuarios/{id}/activo']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/mascotas/mias']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/citas/{id}/estado']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/boletas/mias']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reportes/resumen']").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Una ruta inexistente responde 404 con el formato de error de la API")
    void rutaInexistente() throws Exception {
        como(ADMIN, get("/api/v1/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.ruta", endsWith("/no-existe")))
                .andExpect(jsonPath("$.errores").value(nullValue()))
                .andExpect(jsonPath("$.mensaje", not(containsString("Exception"))));
    }
}
