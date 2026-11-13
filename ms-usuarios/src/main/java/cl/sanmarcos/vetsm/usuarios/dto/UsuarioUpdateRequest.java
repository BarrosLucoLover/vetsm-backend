package cl.sanmarcos.vetsm.usuarios.dto;

import cl.sanmarcos.vetsm.comun.validacion.Validaciones;

import cl.sanmarcos.vetsm.comun.security.Rol;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos que un administrador puede cambiar de una cuenta. La contraseña no se cambia aquí.")
public record UsuarioUpdateRequest(
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres.")
        String nombre,

        @NotBlank(message = "El correo es obligatorio.")
        @Email(message = "Ingresa un correo válido.")
        @Size(max = 150, message = "El correo puede tener hasta 150 caracteres.")
        String email,

        @Pattern(regexp = Validaciones.CELULAR, message = Validaciones.MENSAJE_CELULAR)
        String telefono,

        @NotNull(message = "El rol es obligatorio.")
        Rol rol) {
}
