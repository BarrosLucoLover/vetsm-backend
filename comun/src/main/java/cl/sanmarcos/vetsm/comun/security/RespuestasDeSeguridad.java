package cl.sanmarcos.vetsm.comun.security;

import cl.sanmarcos.vetsm.comun.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Respuestas JSON para los errores de seguridad que ocurren antes de llegar a un controlador,
 * con el mismo formato (ErrorResponse) que el resto de la API.
 */
@Component
public class RespuestasDeSeguridad implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper json;

    public RespuestasDeSeguridad(JsonMapper json) {
        this.json = json;
    }

    /** 401: falta el token o es inválido (alterado, vencido o de otro sistema). */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e) throws IOException {
        boolean traiaToken = request.getHeader("Authorization") != null;
        escribir(request, response, 401, "Unauthorized", traiaToken
                ? "Tu sesión expiró o el token no es válido. Inicia sesión nuevamente."
                : "Debes iniciar sesión para acceder a este recurso.");
    }

    /** 403: hay sesión, pero el rol no tiene permiso. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) throws IOException {
        escribir(request, response, 403, "Forbidden", "Tu cuenta no tiene permiso para realizar esta acción.");
    }

    private void escribir(HttpServletRequest request, HttpServletResponse response, int status, String error, String mensaje) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (status == 401) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        json.writeValue(response.getOutputStream(), new ErrorResponse(Instant.now(), status, error, mensaje, request.getRequestURI(), null));
    }
}
