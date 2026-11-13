package cl.sanmarcos.vetsm.comun.cliente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.sanmarcos.vetsm.comun.exception.ApiException;
import cl.sanmarcos.vetsm.comun.security.JwtConfig;
import cl.sanmarcos.vetsm.comun.security.TokenDeServicio;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prueba el cliente HTTP entre microservicios contra un servidor HTTP falso (sin Spring):
 * que envíe el token de servicio y que traduzca bien los errores del otro servicio.
 */
class ClienteInternoTest {

    private static final String SECRETO = "secreto-de-prueba-con-mas-de-32-caracteres";

    private HttpServer servidor;
    private final AtomicReference<String> autorizacionRecibida = new AtomicReference<>();

    /** Cliente de ejemplo, como los que tiene cada microservicio. */
    static class ClienteDePrueba extends ClienteInterno {
        record Respuesta(String saludo) {
        }

        ClienteDePrueba(String url, TokenDeServicio tokens) {
            super("prueba", url, tokens, JsonMapper.builder().build());
        }

        Respuesta saludar() {
            return llamar(() -> rest.get().uri("/saludo").retrieve().body(Respuesta.class));
        }

        Respuesta fallar() {
            return llamar(() -> rest.get().uri("/stock").retrieve().body(Respuesta.class));
        }
    }

    @BeforeEach
    void levantarServidor() throws Exception {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/saludo", intercambio -> {
            autorizacionRecibida.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            responder(intercambio, 200, "{\"saludo\": \"hola\"}");
        });
        servidor.createContext("/stock", intercambio -> responder(intercambio, 409, """
                {"timestamp": "2026-11-13T12:00:00Z", "status": 409, "error": "Conflict",
                 "mensaje": "Stock insuficiente de \\"Collar antipulgas\\".", "ruta": "/stock", "errores": null}"""));
        servidor.start();
    }

    @AfterEach
    void detenerServidor() {
        servidor.stop(0);
    }

    private static void responder(com.sun.net.httpserver.HttpExchange intercambio, int status, String json) throws java.io.IOException {
        byte[] cuerpo = json.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.sendResponseHeaders(status, cuerpo.length);
        intercambio.getResponseBody().write(cuerpo);
        intercambio.close();
    }

    private TokenDeServicio tokens() {
        JwtConfig config = new JwtConfig();
        return new TokenDeServicio(config.jwtEncoder(config.claveJwt(SECRETO)), Clock.systemUTC(), "vetsm-backend", "ms-prueba");
    }

    private String url() {
        return "http://localhost:" + servidor.getAddress().getPort();
    }

    @Test
    @DisplayName("Cada llamada lleva un token de servicio en la cabecera Authorization")
    void enviaTokenDeServicio() {
        ClienteDePrueba cliente = new ClienteDePrueba(url(), tokens());
        assertThat(cliente.saludar().saludo()).isEqualTo("hola");
        assertThat(autorizacionRecibida.get()).startsWith("Bearer ey");
    }

    @Test
    @DisplayName("Un error del otro servicio se reenvía con el mismo código y mensaje")
    void reenviaErrores() {
        ClienteDePrueba cliente = new ClienteDePrueba(url(), tokens());
        assertThatThrownBy(cliente::fallar)
                .isInstanceOf(ApiException.DeOtroServicio.class)
                .hasMessageContaining("Stock insuficiente")
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(409));
    }

    @Test
    @DisplayName("Si el otro servicio no responde, se responde 503 con un mensaje claro")
    void servicioCaido() {
        String urlApagada = url();
        servidor.stop(0);
        ClienteDePrueba cliente = new ClienteDePrueba(urlApagada, tokens());
        assertThatThrownBy(cliente::saludar)
                .isInstanceOf(ApiException.ServicioNoDisponible.class)
                .hasMessageContaining("no está disponible")
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(503));
    }
}
