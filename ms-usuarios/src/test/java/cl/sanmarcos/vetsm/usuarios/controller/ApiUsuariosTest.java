package cl.sanmarcos.vetsm.usuarios.controller;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
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

/** Pruebas de la API REST de ms-usuarios: CRUD, validaciones, formato de errores y Swagger. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ApiUsuariosTest extends PruebaConToken {

    @Test
    @DisplayName("CRUD de usuarios: POST 201 con Location, GET y PUT")
    void crudUsuario() throws Exception {
        String location = como(ADMIN, post("/api/v1/usuarios"), """
                {"nombre": "Javiera Torres", "email": "javiera@sanmarcos.cl", "password": "Clave1234", "rol": "RECEPCIONISTA"}""")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/usuarios/")))
                .andExpect(jsonPath("$.rol").value("RECEPCIONISTA"))
                .andReturn().getResponse().getHeader("Location");
        String ruta = location.substring(location.indexOf("/api/v1"));

        como(ADMIN, get(ruta)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("javiera@sanmarcos.cl"));
        como(ADMIN, put(ruta), """
                {"nombre": "Javiera Torres P.", "email": "javiera@sanmarcos.cl", "rol": "RECEPCIONISTA"}""")
                .andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Javiera Torres P."));
        como(ADMIN, get("/api/v1/usuarios").param("rol", "RECEPCIONISTA")).andExpect(jsonPath("$", hasSize(2)));
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
    @DisplayName("Un JSON mal formado responde 400 y un usuario inexistente, 404")
    void erroresDeFormato() throws Exception {
        como(ADMIN, post("/api/v1/usuarios"), "{ esto no es json").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("JSON")));
        como(ADMIN, get("/api/v1/usuarios/99999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensaje").value("No existe el usuario 99999."));
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
    @DisplayName("Swagger documenta este servicio y ofrece la documentación de los cuatro microservicios")
    void swagger() throws Exception {
        mvc.perform(get("/v3/api-docs/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value(containsString("ms-usuarios")))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.paths['/api/v1/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/auth/refresh']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/perfil']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/usuarios/{id}/activo']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/reportes/usuarios']").exists())
                // Los endpoints internos no se documentan: no son parte de la API pública.
                .andExpect(jsonPath("$.paths['/internal/v1/usuarios/{id}']").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists());
        mvc.perform(get("/v3/api-docs/usuarios/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.urls", hasSize(4)))
                .andExpect(jsonPath("$.urls[*].name", containsInAnyOrder("ms-usuarios", "ms-catalogo", "ms-ventas", "ms-agenda")));
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
