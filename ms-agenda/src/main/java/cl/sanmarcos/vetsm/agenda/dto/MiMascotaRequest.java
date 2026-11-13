package cl.sanmarcos.vetsm.agenda.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Un cliente registra su propia mascota: el dueño se toma del token, no se envía. */
public record MiMascotaRequest(
        @Schema(example = "Michi")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 60, message = "El nombre puede tener hasta 60 caracteres.")
        String nombre,

        @Schema(example = "Gato")
        @NotBlank(message = "La especie es obligatoria.")
        @Size(max = 40, message = "La especie puede tener hasta 40 caracteres.")
        String especie,

        @Schema(example = "Siamés")
        @Size(max = 60, message = "La raza puede tener hasta 60 caracteres.")
        String raza) {
}
