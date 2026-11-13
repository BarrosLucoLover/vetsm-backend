package cl.sanmarcos.vetsm.comun.cliente;

import cl.sanmarcos.vetsm.comun.exception.ApiException;
import cl.sanmarcos.vetsm.comun.exception.ErrorResponse;
import cl.sanmarcos.vetsm.comun.security.TokenDeServicio;
import java.time.Duration;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

/**
 * Base de los clientes HTTP con los que un microservicio llama a otro (comunicación REST síncrona).
 *
 * - Cada llamada lleva un token de servicio (rol SERVICIO) en la cabecera Authorization.
 * - Si el otro servicio responde un error (por ejemplo 409 "Stock insuficiente"), se reenvía el mismo
 *   código y mensaje, para que el usuario vea el motivo real.
 * - Si el otro servicio no responde, se responde 503 con un mensaje claro en vez de un error 500.
 * - Los tiempos de espera son cortos: un servicio caído no deja colgadas las peticiones del resto.
 */
public abstract class ClienteInterno {

    private static final Logger log = LoggerFactory.getLogger(ClienteInterno.class);

    protected final RestClient rest;
    private final String nombreServicio;

    protected ClienteInterno(String nombreServicio, String urlBase, TokenDeServicio tokens, JsonMapper json) {
        this.nombreServicio = nombreServicio;
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(3));
        fabrica.setReadTimeout(Duration.ofSeconds(10));
        this.rest = RestClient.builder()
                .baseUrl(urlBase)
                .requestFactory(fabrica)
                .requestInterceptor((peticion, cuerpo, ejecucion) -> {
                    peticion.getHeaders().setBearerAuth(tokens.emitir());
                    return ejecucion.execute(peticion, cuerpo);
                })
                .defaultStatusHandler(HttpStatusCode::isError, (peticion, respuesta) -> {
                    int status = respuesta.getStatusCode().value();
                    ErrorResponse error = leerError(json, respuesta.getBody().readAllBytes());
                    if (error == null) {
                        log.warn("{} respondió {} sin un cuerpo de error reconocible.", nombreServicio, status);
                        throw new ApiException.ServicioNoDisponible(nombreServicio);
                    }
                    throw new ApiException.DeOtroServicio(status, error.mensaje(), error.errores());
                })
                .build();
    }

    /** Ejecuta la llamada; si el otro servicio no está disponible responde 503. */
    protected <T> T llamar(Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (ResourceAccessException e) {
            log.warn("No se pudo conectar con {}: {}", nombreServicio, e.getMessage());
            throw new ApiException.ServicioNoDisponible(nombreServicio);
        }
    }

    private static ErrorResponse leerError(JsonMapper json, byte[] cuerpo) {
        try {
            return cuerpo.length == 0 ? null : json.readValue(cuerpo, ErrorResponse.class);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
