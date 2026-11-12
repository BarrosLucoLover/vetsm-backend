package cl.sanmarcos.vetsm.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Base de las pruebas HTTP: inicia sesión de verdad (POST /auth/login) y usa el token como el frontend. */
abstract class PruebaConSesion {

    static final String ADMIN = "admin@sanmarcos.cl";
    static final String RECEPCION = "recepcion@sanmarcos.cl";
    static final String CLIENTE = "ana.garcia@correo.cl";
    private static final Map<String, String> CLAVES = Map.of(ADMIN, "Admin123!", RECEPCION, "Recep123!", CLIENTE, "Cliente123!");

    /** Los tokens de acceso duran 15 minutos: se reutilizan entre pruebas para no iniciar sesión cada vez. */
    private static final Map<String, String> TOKENS = new ConcurrentHashMap<>();

    @Autowired
    protected MockMvc mvc;

    protected String token(String email) throws Exception {
        String guardado = TOKENS.get(email);
        if (guardado != null) {
            return guardado;
        }
        String cuerpo = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, CLAVES.get(email))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String nuevo = JsonPath.read(cuerpo, "$.accessToken");
        TOKENS.put(email, nuevo);
        return nuevo;
    }

    /** Ejecuta la petición con el token del usuario indicado. */
    protected ResultActions como(String email, MockHttpServletRequestBuilder peticion) throws Exception {
        return mvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(email)));
    }

    /** Igual que como(...), con un cuerpo JSON. */
    protected ResultActions como(String email, MockHttpServletRequestBuilder peticion, String json) throws Exception {
        return como(email, peticion.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Petición sin sesión, con un cuerpo JSON. */
    protected ResultActions sinSesion(MockHttpServletRequestBuilder peticion, String json) throws Exception {
        return mvc.perform(peticion.contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
