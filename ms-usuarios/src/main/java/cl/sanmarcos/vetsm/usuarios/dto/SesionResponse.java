package cl.sanmarcos.vetsm.usuarios.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta de login, registro y renovación. El token de renovación NO viene aquí: va en una
 * cookie HttpOnly que el navegador guarda y envía solo, sin que JavaScript pueda leerla.
 */
public record SesionResponse(
        @Schema(description = "JWT para la cabecera Authorization: Bearer <token>")
        String accessToken,

        @Schema(example = "Bearer")
        String tipo,

        @Schema(example = "900", description = "Segundos de vida del token de acceso")
        long expiraEn,

        UsuarioResponse usuario) {
}
