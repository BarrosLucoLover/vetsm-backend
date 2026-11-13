package cl.sanmarcos.vetsm.usuarios.dto;

import cl.sanmarcos.vetsm.comun.validacion.Validaciones;

import cl.sanmarcos.vetsm.comun.security.Rol;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos con los que un administrador crea una cuenta (de cualquier rol)")
public record UsuarioRequest(
        @Schema(example = "Javiera Torres")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres.")
        String nombre,

        @Schema(example = "javiera.torres@sanmarcos.cl")
        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "Ingresa un correo válido.")
        @Size(max = 150, message = "El correo puede tener hasta 150 caracteres.")
        String email,

        @Schema(example = "Clave1234")
        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
        String password,

        @Schema(example = "+56 9 1234 5678")
        @Pattern(regexp = Validaciones.CELULAR, message = Validaciones.MENSAJE_CELULAR)
        String telefono,

        @Schema(example = "RECEPCIONISTA")
        @NotNull(message = "El rol es obligatorio.")
        Rol rol) {
}
