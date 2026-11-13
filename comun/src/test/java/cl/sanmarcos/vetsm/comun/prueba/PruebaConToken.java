package cl.sanmarcos.vetsm.comun.prueba;

import cl.sanmarcos.vetsm.comun.security.JwtConfig;
import cl.sanmarcos.vetsm.comun.security.TokenDeServicio;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base de las pruebas HTTP de los microservicios. Crea tokens JWT iguales a los que emite
 * ms-usuarios (mismo secreto, emisor y claims), así cada servicio se prueba solo, sin tener que
 * levantar ms-usuarios para iniciar sesión.
 */
public abstract class PruebaConToken {

    /** Usuario de prueba: los ids coinciden con las cuentas de demostración de ms-usuarios. */
    public record Usuario(Long id, String rol, String nombre, String email) {
    }

    public static final Usuario ADMIN = new Usuario(1L, "ADMIN", "Administrador General", "admin@sanmarcos.cl");
    public static final Usuario RECEPCION = new Usuario(2L, "RECEPCIONISTA", "Camila Rojas", "recepcion@sanmarcos.cl");
    public static final Usuario CLIENTE = new Usuario(3L, "CLIENTE", "Ana García", "ana.garcia@correo.cl");
    public static final Usuario OTRO_CLIENTE = new Usuario(50L, "CLIENTE", "Pedro Soto", "pedro@correo.cl");

    @Autowired
    protected MockMvc mvc;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private TokenDeServicio tokenDeServicio;

    @Value("${app.jwt.emisor}")
    private String emisor;

    protected String token(Usuario usuario) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(emisor)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(15, ChronoUnit.MINUTES))
                .subject(String.valueOf(usuario.id()))
                .claim(JwtConfig.CLAIM_ROL, usuario.rol())
                .claim(JwtConfig.CLAIM_NOMBRE, usuario.nombre())
                .claim(JwtConfig.CLAIM_EMAIL, usuario.email())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    /** Ejecuta la petición con el token del usuario indicado. */
    protected ResultActions como(Usuario usuario, MockHttpServletRequestBuilder peticion) throws Exception {
        return mvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token(usuario)));
    }

    /** Igual que como(...), con un cuerpo JSON. */
    protected ResultActions como(Usuario usuario, MockHttpServletRequestBuilder peticion, String json) throws Exception {
        return como(usuario, peticion.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Como lo llamaría otro microservicio (token con rol SERVICIO). */
    protected ResultActions comoServicio(MockHttpServletRequestBuilder peticion) throws Exception {
        return mvc.perform(peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeServicio.emitir()));
    }

    protected ResultActions comoServicio(MockHttpServletRequestBuilder peticion, String json) throws Exception {
        return comoServicio(peticion.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Petición sin sesión, con un cuerpo JSON. */
    protected ResultActions sinSesion(MockHttpServletRequestBuilder peticion, String json) throws Exception {
        return mvc.perform(peticion.contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
