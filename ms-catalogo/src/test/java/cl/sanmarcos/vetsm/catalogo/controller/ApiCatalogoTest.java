package cl.sanmarcos.vetsm.catalogo.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.sanmarcos.vetsm.comun.prueba.PruebaConToken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Pruebas de la API REST de ms-catalogo: catálogo público, CRUD del administrador, inventario interno y Swagger. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiCatalogoTest extends PruebaConToken {

    @Test
    @DisplayName("GET /productos es público y devuelve el catálogo con los campos que usa el frontend")
    void catalogo() throws Exception {
        mvc.perform(get("/api/v1/productos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(12)))
                .andExpect(jsonPath("$[?(@.nombre == 'Collar antipulgas')].tipo").value(hasItem("PRODUCTO")))
                .andExpect(jsonPath("$[?(@.nombre == 'Collar antipulgas')].categoriaNombre").value(hasItem("Accesorios")));
        mvc.perform(get("/api/v1/productos").param("tipo", "SERVICIO")).andExpect(jsonPath("$", hasSize(6)));
        mvc.perform(get("/api/v1/categorias")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(4)));
    }

    @Test
    @DisplayName("CRUD completo: POST 201 con Location, GET, PUT y DELETE 204")
    void crudCategoria() throws Exception {
        String location = como(ADMIN, post("/api/v1/categorias"), """
                {"nombre": "Farmacia", "descripcion": "Medicamentos veterinarios"}""")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/categorias/")))
                .andReturn().getResponse().getHeader("Location");
        String ruta = location.substring(location.indexOf("/api/v1"));

        mvc.perform(get(ruta)).andExpect(status().isOk()).andExpect(jsonPath("$.descripcion").value("Medicamentos veterinarios"));
        como(ADMIN, put(ruta), """
                {"nombre": "Farmacia veterinaria"}""").andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Farmacia veterinaria"));
        como(ADMIN, delete(ruta)).andExpect(status().isNoContent());
        mvc.perform(get(ruta)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Un JSON mal formado o un id inválido responden 400; un producto inexistente, 404")
    void erroresDeFormato() throws Exception {
        como(ADMIN, post("/api/v1/categorias"), "{ esto no es json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("JSON")));
        mvc.perform(get("/api/v1/productos/abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/productos/99999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe el producto 99999."));
    }

    @Test
    @DisplayName("Una categoría con productos no se elimina: 409 con un mensaje claro")
    void conflicto() throws Exception {
        como(ADMIN, delete("/api/v1/categorias/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("tiene productos")));
    }

    @Test
    @DisplayName("Solo el administrador modifica el catálogo y ve el reporte de inventario")
    void soloAdmin() throws Exception {
        String producto = """
                {"nombre": "Correa", "precio": 9990, "tipo": "PRODUCTO", "stock": 3, "categoriaId": 3}""";
        como(CLIENTE, post("/api/v1/productos"), producto).andExpect(status().isForbidden());
        como(RECEPCION, post("/api/v1/productos"), producto).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/productos").contentType("application/json").content(producto)).andExpect(status().isUnauthorized());
        como(ADMIN, post("/api/v1/productos"), producto).andExpect(status().isCreated());

        como(ADMIN, get("/api/v1/reportes/inventario"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productosConStockBajo[*].nombre").value(hasItem("Correa")));
        como(CLIENTE, get("/api/v1/reportes/inventario")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El inventario interno solo lo usa otro microservicio: reserva el stock y entrega los precios")
    void inventarioInterno() throws Exception {
        String pedido = """
                {"items": [{"productoId": 9, "cantidad": 2}, {"productoId": 1, "cantidad": 1}]}""";
        como(ADMIN, post("/internal/v1/inventario/reservas"), pedido).andExpect(status().isForbidden());
        como(CLIENTE, post("/internal/v1/inventario/reservas"), pedido).andExpect(status().isForbidden());

        comoServicio(post("/internal/v1/inventario/reservas"), pedido)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].nombre").value("Collar antipulgas"))
                .andExpect(jsonPath("$.items[0].precio").value(12990))
                .andExpect(jsonPath("$.items[1].tipo").value("SERVICIO"));
        mvc.perform(get("/api/v1/productos/9")).andExpect(jsonPath("$.stock").value(28));

        comoServicio(post("/internal/v1/inventario/devoluciones"), pedido).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/productos/9")).andExpect(jsonPath("$.stock").value(30));
    }

    @Test
    @DisplayName("Sin stock suficiente la reserva responde 409 y no descuenta nada (todo o nada)")
    void reservaSinStock() throws Exception {
        comoServicio(post("/internal/v1/inventario/reservas"), """
                {"items": [{"productoId": 9, "cantidad": 1}, {"productoId": 12, "cantidad": 500}]}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("Stock insuficiente de \"Arena sanitaria 10 kg\"")));
    }

    @Test
    @DisplayName("Swagger documenta la API pública de ms-catalogo, sin los endpoints internos")
    void swagger() throws Exception {
        mvc.perform(get("/v3/api-docs/catalogo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value(containsString("ms-catalogo")))
                .andExpect(jsonPath("$.paths['/api/v1/productos/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/categorias']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reportes/inventario']").exists())
                .andExpect(jsonPath("$.paths['/internal/v1/inventario/reservas']").doesNotExist());
    }
}
