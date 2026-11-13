package cl.sanmarcos.vetsm.usuarios.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.sanmarcos.vetsm.comun.prueba.PruebaConToken;
import cl.sanmarcos.vetsm.usuarios.model.RefreshToken;
import cl.sanmarcos.vetsm.usuarios.repository.RefreshTokenRepository;
import cl.sanmarcos.vetsm.usuarios.security.CookieSesion;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pruebas de seguridad de ms-usuarios: login real, contenido del token, tokens inválidos,
 * permisos por rol, renovación con rotación, detección de robo, cierre de sesión y CORS.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SeguridadTest extends PruebaConToken {

    @Autowired private JwtEncoder encoder;
    @Autowired private JwtDecoder decoder;
    @Autowired private RefreshTokenRepository refreshTokens;

    private MockHttpServletResponse login(String email, String password) throws Exception {
        return sinSesion(post("/api/v1/auth/login"), "{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password))
                .andReturn().getResponse();
    }

    private static Cookie cookieDe(MockHttpServletResponse respuesta) {
        Cookie cookie = respuesta.getCookie(CookieSesion.NOMBRE);
        assertThat(cookie).as("la respuesta debe traer la cookie de renovación").isNotNull();
        return new Cookie(cookie.getName(), cookie.getValue());
    }

    private MockHttpServletResponse renovar(Cookie cookie) throws Exception {
        return mvc.perform(post("/api/v1/auth/refresh").cookie(cookie)).andReturn().getResponse();
    }

    private String registrar(String nombre, String email) throws Exception {
        String cuerpo = sinSesion(post("/api/v1/auth/registro"), """
                {"nombre": "%s", "email": "%s", "password": "Clave1234"}""".formatted(nombre, email))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(cuerpo, "$.accessToken");
    }

    private String firmar(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Nested
    @DisplayName("Inicio de sesión")
    class InicioDeSesion {

        @Test
        @DisplayName("Entrega el token de acceso en el cuerpo y el de renovación en una cookie HttpOnly y SameSite=Strict")
        void loginCorrecto() throws Exception {
            MockHttpServletResponse respuesta = login(ADMIN.email(), "Admin123!");
            assertThat(respuesta.getStatus()).isEqualTo(200);

            String cuerpo = respuesta.getContentAsString();
            assertThat((String) JsonPath.read(cuerpo, "$.tipo")).isEqualTo("Bearer");
            assertThat((Integer) JsonPath.read(cuerpo, "$.expiraEn")).isEqualTo(900);
            assertThat((String) JsonPath.read(cuerpo, "$.usuario.rol")).isEqualTo("ADMIN");

            String setCookie = respuesta.getHeader(HttpHeaders.SET_COOKIE);
            assertThat(setCookie).contains(CookieSesion.NOMBRE + "=").contains("HttpOnly").contains("SameSite=Strict").contains("Path=/api/v1/auth");
            // El token de renovación nunca viaja en el cuerpo, donde JavaScript podría leerlo.
            assertThat(cuerpo).doesNotContain(cookieDe(respuesta).getValue());
        }

        @Test
        @DisplayName("El token lleva el id, el rol, el nombre y el correo, firmado y con vencimiento de 15 minutos")
        void contenidoDelToken() throws Exception {
            String acceso = JsonPath.read(login(CLIENTE.email(), "Cliente123!").getContentAsString(), "$.accessToken");
            Jwt jwt = decoder.decode(acceso);
            assertThat(jwt.getSubject()).isEqualTo("3");
            assertThat(jwt.getClaimAsString("rol")).isEqualTo("CLIENTE");
            assertThat(jwt.getClaimAsString("nombre")).isEqualTo("Ana García");
            assertThat(jwt.getClaimAsString("email")).isEqualTo(CLIENTE.email());
            assertThat(jwt.getClaimAsString("iss")).isEqualTo("vetsm-backend");
            assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
            assertThat(ChronoUnit.MINUTES.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(15);
        }

        @Test
        @DisplayName("Contraseña incorrecta y correo inexistente responden lo mismo (401), sin revelar qué correos existen")
        void credencialesIncorrectas() throws Exception {
            MockHttpServletResponse claveMala = login(ADMIN.email(), "Incorrecta1");
            MockHttpServletResponse correoInexistente = login("nadie@sanmarcos.cl", "Incorrecta1");
            assertThat(claveMala.getStatus()).isEqualTo(401);
            assertThat(correoInexistente.getStatus()).isEqualTo(401);
            assertThat((String) JsonPath.read(claveMala.getContentAsString(), "$.mensaje"))
                    .isEqualTo(JsonPath.read(correoInexistente.getContentAsString(), "$.mensaje"))
                    .isEqualTo("Correo o contraseña incorrectos.");
            assertThat(claveMala.getCookie(CookieSesion.NOMBRE)).isNull();
        }

        @Test
        @DisplayName("Una cuenta desactivada no puede iniciar sesión")
        void cuentaDesactivada() throws Exception {
            como(ADMIN, patch("/api/v1/usuarios/" + RECEPCION.id() + "/activo"), "{\"activo\": false}").andExpect(status().isOk());
            MockHttpServletResponse respuesta = login(RECEPCION.email(), "Recep123!");
            assertThat(respuesta.getStatus()).isEqualTo(401);
            assertThat(respuesta.getContentAsString()).contains("desactivada");
        }

        @Test
        @DisplayName("El registro público crea siempre un CLIENTE, aunque se envíe otro rol")
        void registroComoCliente() throws Exception {
            sinSesion(post("/api/v1/auth/registro"), """
                    {"nombre": "Intruso", "email": "intruso@correo.cl", "password": "Clave1234", "rol": "ADMIN"}""")
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.usuario.rol").value("CLIENTE"))
                    .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString(CookieSesion.NOMBRE)));
        }
    }

    @Nested
    @DisplayName("Tokens inválidos")
    class TokensInvalidos {

        @Test
        @DisplayName("Sin token, un recurso protegido responde 401 en JSON")
        void sinToken() throws Exception {
            mvc.perform(get("/api/v1/perfil"))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Bearer")))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.mensaje").value("Debes iniciar sesión para acceder a este recurso."));
        }

        @Test
        @DisplayName("Un token alterado (firma inválida) responde 401")
        void tokenAlterado() throws Exception {
            String original = token(CLIENTE);
            // Se cambia el rol dentro del token, sin poder volver a firmarlo.
            String[] partes = original.split("\\.");
            String carga = new String(Base64.getUrlDecoder().decode(partes[1])).replace("CLIENTE", "ADMIN");
            String falso = partes[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(carga.getBytes()) + "." + partes[2];
            mvc.perform(get("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + falso))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.mensaje").value(containsString("token no es válido")));
        }

        @Test
        @DisplayName("Un token vencido responde 401")
        void tokenVencido() throws Exception {
            Instant haceUnaHora = Instant.now().minus(1, ChronoUnit.HOURS);
            String vencido = firmar(JwtClaimsSet.builder().issuer("vetsm-backend").subject("1").claim("rol", "ADMIN")
                    .issuedAt(haceUnaHora).expiresAt(haceUnaHora.plus(15, ChronoUnit.MINUTES)).build());
            mvc.perform(get("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + vencido))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Un token de otro emisor responde 401")
        void otroEmisor() throws Exception {
            String ajeno = firmar(JwtClaimsSet.builder().issuer("otro-sistema").subject("1").claim("rol", "ADMIN")
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plus(5, ChronoUnit.MINUTES)).build());
            mvc.perform(get("/api/v1/usuarios").header(HttpHeaders.AUTHORIZATION, "Bearer " + ajeno))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Permisos por rol")
    class PermisosPorRol {

        @Test
        @DisplayName("Solo el administrador gestiona usuarios y ve el reporte; los demás reciben 403 en JSON")
        void soloAdmin() throws Exception {
            como(ADMIN, get("/api/v1/usuarios")).andExpect(status().isOk());
            como(ADMIN, get("/api/v1/reportes/usuarios")).andExpect(status().isOk()).andExpect(jsonPath("$.clientesActivos").value(1));

            como(RECEPCION, get("/api/v1/usuarios"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.status").value(403))
                    .andExpect(jsonPath("$.mensaje").value("Tu cuenta no tiene permiso para realizar esta acción."));
            como(CLIENTE, get("/api/v1/usuarios")).andExpect(status().isForbidden());
            como(RECEPCION, get("/api/v1/reportes/usuarios")).andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Un administrador no puede desactivarse a sí mismo")
        void noDesactivarseASiMismo() throws Exception {
            como(ADMIN, patch("/api/v1/usuarios/" + ADMIN.id() + "/activo"), "{\"activo\": false}")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.mensaje").value("No puedes desactivar tu propia cuenta."));
        }

        @Test
        @DisplayName("Los endpoints internos solo aceptan el token de otro microservicio, ni siquiera el de un administrador")
        void endpointsInternos() throws Exception {
            comoServicio(get("/internal/v1/usuarios/3")).andExpect(status().isOk()).andExpect(jsonPath("$.nombre").value("Ana García"));
            como(ADMIN, get("/internal/v1/usuarios/3")).andExpect(status().isForbidden());
            mvc.perform(get("/internal/v1/usuarios/3")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @DisplayName("Renovación y cierre de sesión")
    class Renovacion {

        @Test
        @DisplayName("Renovar entrega un token nuevo y cambia la cookie; el token usado no vuelve a servir")
        void rotacion() throws Exception {
            Cookie primera = cookieDe(login(CLIENTE.email(), "Cliente123!"));
            MockHttpServletResponse renovada = renovar(primera);
            assertThat(renovada.getStatus()).isEqualTo(200);
            assertThat((String) JsonPath.read(renovada.getContentAsString(), "$.accessToken")).isNotBlank();
            Cookie segunda = cookieDe(renovada);
            assertThat(segunda.getValue()).isNotEqualTo(primera.getValue());

            assertThat(renovar(segunda).getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("Si un token ya usado aparece otra vez (posible robo), se cierran todas las sesiones")
        void deteccionDeRobo() throws Exception {
            Cookie robada = cookieDe(login(CLIENTE.email(), "Cliente123!"));
            Cookie legitima = cookieDe(renovar(robada));
            // El ladrón la usa un minuto después (fuera del margen para pestañas simultáneas).
            refreshTokens.findAll().stream().filter(RefreshToken::isReemplazado)
                    .forEach(t -> t.setReemplazadoEn(t.getReemplazadoEn().minusSeconds(60)));

            assertThat(renovar(robada).getStatus()).isEqualTo(401);
            // La sesión legítima también se cerró: hay que volver a iniciar sesión.
            assertThat(renovar(legitima).getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("Dos pestañas que renuevan a la vez: la segunda recibe 401, pero la sesión sigue abierta")
        void renovacionSimultanea() throws Exception {
            Cookie compartida = cookieDe(login(CLIENTE.email(), "Cliente123!"));
            Cookie nueva = cookieDe(renovar(compartida));

            assertThat(renovar(compartida).getStatus()).isEqualTo(401);
            assertThat(renovar(nueva).getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("Sin cookie o con una cookie inventada, renovar responde 401")
        void sinCookie() throws Exception {
            mvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isUnauthorized());
            assertThat(renovar(new Cookie(CookieSesion.NOMBRE, "inventado")).getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("Cerrar sesión revoca el token de renovación y borra la cookie")
        void logout() throws Exception {
            Cookie cookie = cookieDe(login(CLIENTE.email(), "Cliente123!"));
            MockHttpServletResponse salida = mvc.perform(post("/api/v1/auth/logout").cookie(cookie)).andReturn().getResponse();
            assertThat(salida.getStatus()).isEqualTo(204);
            assertThat(salida.getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
            assertThat(renovar(cookie).getStatus()).isEqualTo(401);
        }

        @Test
        @DisplayName("Al cambiar la contraseña se cierran las otras sesiones y esta recibe una cookie nueva")
        void cambioDePassword() throws Exception {
            String acceso = registrar("Luc Pérez", "luc@correo.cl");
            Cookie otroDispositivo = cookieDe(login("luc@correo.cl", "Clave1234"));

            MockHttpServletResponse cambio = mvc.perform(put("/api/v1/perfil/password")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + acceso)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"actual\": \"Clave1234\", \"nueva\": \"NuevaClave1\"}"))
                    .andReturn().getResponse();
            assertThat(cambio.getStatus()).isEqualTo(204);
            Cookie nueva = cookieDe(cambio);

            assertThat(renovar(otroDispositivo).getStatus()).isEqualTo(401);
            assertThat(renovar(nueva).getStatus()).isEqualTo(200);
            assertThat(login("luc@correo.cl", "Clave1234").getStatus()).isEqualTo(401);
            assertThat(login("luc@correo.cl", "NuevaClave1").getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("Datos propios")
    class DatosPropios {

        @Test
        @DisplayName("Perfil: cada usuario ve y edita el suyo (el id sale del token, no de la URL)")
        void perfil() throws Exception {
            como(CLIENTE, get("/api/v1/perfil")).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(CLIENTE.email()));
            como(CLIENTE, put("/api/v1/perfil"), """
                    {"nombre": "Ana García", "telefono": "+56 9 8765 4321", "region": "Maule", "comuna": "Talca",
                     "avatarIcono": "🐱", "avatarColor": "#8E44AD"}""")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.avatarColor").value("#8E44AD"))
                    .andExpect(jsonPath("$.rol").value("CLIENTE"));
        }
    }

    @Nested
    @DisplayName("CORS")
    class Cors {

        @Test
        @DisplayName("El frontend propio puede llamar a la API, incluso enviando la cookie")
        void origenPermitido() throws Exception {
            mvc.perform(options("/api/v1/auth/login")
                            .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
        }

        @Test
        @DisplayName("Otro sitio web no puede llamar a la API desde el navegador")
        void origenBloqueado() throws Exception {
            mvc.perform(options("/api/v1/auth/login")
                            .header(HttpHeaders.ORIGIN, "https://sitio-malicioso.example")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                    .andExpect(status().isForbidden());
        }
    }
}
