package cl.sanmarcos.vetsm.agenda.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.sanmarcos.vetsm.agenda.cliente.CatalogoCliente;
import cl.sanmarcos.vetsm.agenda.cliente.UsuariosCliente;
import cl.sanmarcos.vetsm.comun.prueba.PruebaConToken;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** Pruebas de la API REST de ms-agenda: flujo de una cita, permisos por rol, datos propios, reporte y endpoints internos. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiAgendaTest extends PruebaConToken {

    @Autowired private Clock reloj;

    @MockitoBean private CatalogoCliente catalogo;
    @MockitoBean private UsuariosCliente usuarios;

    @BeforeEach
    void otrosServicios() {
        when(catalogo.obtener(anyLong())).thenReturn(new CatalogoCliente.Producto(2L, "Vacunación", "SERVICIO", true));
        when(catalogo.serviciosActivos()).thenReturn(List.of(
                new CatalogoCliente.Producto(1L, "Consulta General", "SERVICIO", true),
                new CatalogoCliente.Producto(2L, "Vacunación", "SERVICIO", true),
                new CatalogoCliente.Producto(4L, "Odontología", "SERVICIO", true)));
        when(usuarios.obtener(3L)).thenReturn(new UsuariosCliente.Usuario(3L, "Ana García", "+56 9 8765 4321", "CLIENTE", true));
    }

    @Test
    @DisplayName("Flujo de una cita: se solicita sin sesión, la recepción la confirma y una transición inválida responde 409")
    void flujoCita() throws Exception {
        String fecha = LocalDate.now(reloj).plusDays(9).toString();
        String location = sinSesion(post("/api/v1/citas"), """
                {"nombreMascota": "Luna", "nombreDueno": "Pedro Soto", "telefonoContacto": "+56 9 1122 3344",
                 "servicioId": 2, "fecha": "%s", "hora": "10:30"}""".formatted(fecha))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.hora").value("10:30"))
                .andExpect(jsonPath("$.servicioNombre").value("Vacunación"))
                .andReturn().getResponse().getHeader("Location");
        String ruta = location.substring(location.indexOf("/api/v1"));

        como(RECEPCION, patch(ruta + "/estado"), """
                {"estado": "CONFIRMADA"}""").andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADA"));
        como(RECEPCION, patch(ruta + "/estado"), """
                {"estado": "PENDIENTE"}""").andExpect(status().isConflict());

        // El mismo horario ya está ocupado.
        sinSesion(post("/api/v1/citas"), """
                {"nombreMascota": "Rocky", "nombreDueno": "María", "telefonoContacto": "912345678",
                 "servicioId": 2, "fecha": "%s", "hora": "10:30"}""".formatted(fecha))
                .andExpect(status().isConflict());

        como(RECEPCION, get("/api/v1/citas").param("fecha", fecha)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("La agenda la gestionan el administrador y la recepción; un cliente recibe 403")
    void agenda() throws Exception {
        como(ADMIN, get("/api/v1/citas")).andExpect(status().isOk());
        como(RECEPCION, get("/api/v1/citas")).andExpect(status().isOk());
        como(RECEPCION, get("/api/v1/mascotas")).andExpect(status().isOk());
        como(CLIENTE, get("/api/v1/citas")).andExpect(status().isForbidden());
        como(CLIENTE, get("/api/v1/mascotas")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/citas")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Mascotas: el cliente ve las suyas y registra nuevas a su nombre")
    void mascotasPropias() throws Exception {
        como(CLIENTE, get("/api/v1/mascotas/mias")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(2)));
        como(CLIENTE, post("/api/v1/mascotas/mias"), """
                {"nombre": "Michi", "especie": "Gato"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.duenoNombre").value("Ana García"));
        como(CLIENTE, get("/api/v1/mascotas/mias")).andExpect(jsonPath("$", hasSize(3)));
        como(OTRO_CLIENTE, get("/api/v1/mascotas/mias")).andExpect(jsonPath("$", hasSize(0)));
        como(CLIENTE, get("/api/v1/citas/mias")).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("El reporte de citas lo ve solo el administrador e incluye los servicios sin citas")
    void reporte() throws Exception {
        como(ADMIN, get("/api/v1/reportes/citas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.citasPorEstado.PENDIENTE").value(1))
                .andExpect(jsonPath("$.citasPorEstado.CONFIRMADA").value(1))
                .andExpect(jsonPath("$.citasPorServicio", hasSize(3)))
                .andExpect(jsonPath("$.citasPorServicio[2].etiqueta").value("Odontología"))
                .andExpect(jsonPath("$.citasPorServicio[2].valor").value(0));
        como(RECEPCION, get("/api/v1/reportes/citas")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("El endpoint interno de referencias solo acepta el token de otro microservicio")
    void endpointInterno() throws Exception {
        comoServicio(get("/internal/v1/clientes/3/referencias")).andExpect(status().isOk()).andExpect(jsonPath("$.cantidad").value(2));
        como(ADMIN, get("/internal/v1/clientes/3/referencias")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Swagger documenta la API pública de ms-agenda, sin los endpoints internos")
    void swagger() throws Exception {
        mvc.perform(get("/v3/api-docs/agenda"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value(containsString("ms-agenda")))
                .andExpect(jsonPath("$.paths['/api/v1/citas/{id}/estado']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/mascotas/mias']").exists())
                .andExpect(jsonPath("$.paths['/internal/v1/clientes/{id}/referencias']").doesNotExist());
    }
}
