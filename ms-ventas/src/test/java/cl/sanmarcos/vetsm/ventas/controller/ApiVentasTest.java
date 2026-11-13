package cl.sanmarcos.vetsm.ventas.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.sanmarcos.vetsm.comun.prueba.PruebaConToken;
import cl.sanmarcos.vetsm.ventas.cliente.CatalogoCliente;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** Pruebas de la API REST de ms-ventas: compra, permisos por rol, datos propios, reporte y endpoints internos. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiVentasTest extends PruebaConToken {

    private static final String COMPRA = """
            {"items": [{"productoId": 9, "cantidad": 2}],
             "pago": {"tarjeta": {"numero": "%s", "titular": "Ana García", "vencimiento": "12/35"}, "cvv": "123"}}""";

    @MockitoBean private CatalogoCliente catalogo;

    @BeforeEach
    void catalogoSimulado() {
        when(catalogo.reservar(anyList())).thenReturn(new CatalogoCliente.Reserva(List.of(
                new CatalogoCliente.ItemReservado(9L, "Collar antipulgas", "PRODUCTO", 12990, 2))));
    }

    private String comprar(Usuario usuario) throws Exception {
        String location = como(usuario, post("/api/v1/boletas"), COMPRA.formatted("4111 1111 1111 1111"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        return location.substring(location.indexOf("/api/v1"));
    }

    @Test
    @DisplayName("Compra: 201 con la boleta; la tarjeta rechazada responde 402 y la mal escrita, 400")
    void compra() throws Exception {
        como(CLIENTE, post("/api/v1/boletas"), COMPRA.formatted("4111 1111 1111 1111"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clienteEmail").value(CLIENTE.email()))
                .andExpect(jsonPath("$.total").value(25980))
                .andExpect(jsonPath("$.medioPago.marcaNombre").value("Visa"))
                .andExpect(jsonPath("$.medioPago.codigoAutorizacion").value(matchesPattern("\\d{6}")))
                .andExpect(jsonPath("$.detalles[0].precioUnitario").value(12990))
                .andExpect(resultado -> {
                    if (resultado.getResponse().getContentAsString().contains("4111111111111111")) {
                        throw new AssertionError("La respuesta contiene el número completo de la tarjeta");
                    }
                });
        como(CLIENTE, post("/api/v1/boletas"), COMPRA.formatted("4000 0000 0000 0002"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.mensaje").value(containsString("rechazó")));
        como(CLIENTE, post("/api/v1/boletas"), COMPRA.formatted("4111 1111 1111 1112"))
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
    @DisplayName("Solo un cliente compra y guarda tarjetas; el personal recibe 403 y sin sesión, 401")
    void soloCliente() throws Exception {
        como(ADMIN, get("/api/v1/tarjetas")).andExpect(status().isForbidden());
        como(RECEPCION, get("/api/v1/boletas/mias")).andExpect(status().isForbidden());
        como(ADMIN, post("/api/v1/boletas"), COMPRA.formatted("4111 1111 1111 1111")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/boletas/mias")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Compras: el cliente compra a su nombre y no puede ver boletas ajenas")
    void comprasPropias() throws Exception {
        String rutaBoleta = comprar(CLIENTE);
        como(CLIENTE, get("/api/v1/boletas/mias")).andExpect(jsonPath("$", hasSize(1)));
        como(CLIENTE, get(rutaBoleta)).andExpect(status().isOk());
        como(ADMIN, get(rutaBoleta)).andExpect(status().isOk());
        como(OTRO_CLIENTE, get(rutaBoleta)).andExpect(status().isNotFound());
        como(OTRO_CLIENTE, get("/api/v1/boletas/mias")).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("Tarjetas: un cliente no ve ni borra las tarjetas de otro")
    void tarjetasPropias() throws Exception {
        String location = como(CLIENTE, post("/api/v1/tarjetas"), """
                {"numero": "5555 5555 5555 4444", "titular": "Ana García", "vencimiento": "08/34"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ultimos4").value("4444"))
                .andExpect(jsonPath("$.predeterminada").value(true))
                .andReturn().getResponse().getHeader("Location");
        String idTarjeta = location.substring(location.lastIndexOf('/') + 1);

        como(OTRO_CLIENTE, get("/api/v1/tarjetas")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
        como(OTRO_CLIENTE, delete("/api/v1/tarjetas/" + idTarjeta)).andExpect(status().isNotFound());
        como(CLIENTE, get("/api/v1/tarjetas")).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("El reporte de ventas lo ve solo el administrador")
    void reporte() throws Exception {
        comprar(CLIENTE);
        como(ADMIN, get("/api/v1/reportes/ventas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.boletasPagadas").value(1))
                .andExpect(jsonPath("$.ventasTotales").value(25980))
                .andExpect(jsonPath("$.ventasPorProducto[0].nombre").value("Collar antipulgas"));
        como(RECEPCION, get("/api/v1/reportes/ventas")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Los endpoints internos (referencias y borrar tarjetas) solo aceptan el token de otro microservicio")
    void endpointsInternos() throws Exception {
        comprar(CLIENTE);
        comoServicio(get("/internal/v1/clientes/3/referencias")).andExpect(status().isOk()).andExpect(jsonPath("$.cantidad").value(1));
        como(ADMIN, get("/internal/v1/clientes/3/referencias")).andExpect(status().isForbidden());
        comoServicio(delete("/internal/v1/clientes/3/tarjetas")).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("Swagger documenta la API pública de ms-ventas, sin los endpoints internos")
    void swagger() throws Exception {
        mvc.perform(get("/v3/api-docs/ventas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value(containsString("ms-ventas")))
                .andExpect(jsonPath("$.paths['/api/v1/boletas/mias']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/tarjetas']").exists())
                .andExpect(jsonPath("$.paths['/internal/v1/clientes/{id}/referencias']").doesNotExist());
    }
}
