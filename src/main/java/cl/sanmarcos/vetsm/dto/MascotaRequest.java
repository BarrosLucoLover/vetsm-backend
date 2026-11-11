package cl.sanmarcos.vetsm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MascotaRequest(
        @Schema(example = "Pepe")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 60, message = "El nombre puede tener hasta 60 caracteres.")
        String nombre,

        @Schema(example = "Perro")
        @NotBlank(message = "La especie es obligatoria.")
        @Size(max = 40, message = "La especie puede tener hasta 40 caracteres.")
        String especie,

        @Schema(example = "Mestizo")
        @Size(max = 60, message = "La raza puede tener hasta 60 caracteres.")
        String raza,

        @Schema(example = "3", description = "Id del dueño (un usuario con rol CLIENTE)")
        @NotNull(message = "El dueño es obligatorio.")
        Long duenoId) {
}
