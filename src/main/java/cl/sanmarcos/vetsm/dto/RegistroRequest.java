package cl.sanmarcos.vetsm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Registro público. No tiene campo de rol: toda cuenta creada así es de CLIENTE. */
@Schema(description = "Registro público de un cliente. El rol no se puede elegir: siempre es CLIENTE.")
public record RegistroRequest(
        @Schema(example = "Luc Pérez")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres.")
        String nombre,

        @Schema(example = "luc@correo.cl")
        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "Ingresa un correo válido.")
        @Size(max = 150, message = "El correo puede tener hasta 150 caracteres.")
        String email,

        @Schema(example = "Clave1234")
        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
        String password) {
}
