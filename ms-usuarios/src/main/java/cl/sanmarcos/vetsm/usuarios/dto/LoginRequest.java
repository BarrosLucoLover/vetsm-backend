package cl.sanmarcos.vetsm.usuarios.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "admin@sanmarcos.cl")
        @NotBlank(message = "Ingresa tu correo.")
        String email,

        @Schema(example = "Admin123!")
        @NotBlank(message = "Ingresa tu contraseña.")
        String password) {
}
