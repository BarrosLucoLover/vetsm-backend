package cl.sanmarcos.vetsm.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

/** Formato único de todos los errores de la API, para que el frontend los muestre siempre igual. */
@Schema(description = "Respuesta de error de la API")
public record ErrorResponse(
        @Schema(example = "2026-11-11T14:30:00Z") Instant timestamp,
        @Schema(example = "409") int status,
        @Schema(example = "Conflict") String error,
        @Schema(example = "Ya hay una cita agendada para ese día y hora.") String mensaje,
        @Schema(example = "/api/v1/citas") String ruta,
        @Schema(description = "Errores por campo, cuando el problema está en los datos enviados",
                example = "{\"email\": \"Ingresa un correo válido.\"}")
        Map<String, String> errores) {
}
