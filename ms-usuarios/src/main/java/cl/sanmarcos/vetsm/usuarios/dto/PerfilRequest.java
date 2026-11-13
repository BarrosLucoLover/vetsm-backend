package cl.sanmarcos.vetsm.usuarios.dto;

import cl.sanmarcos.vetsm.comun.validacion.Validaciones;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos de \"Mi cuenta\" que el propio usuario puede personalizar")
public record PerfilRequest(
        @Schema(example = "Ana García")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(min = 3, max = 100, message = "El nombre debe tener entre 3 y 100 caracteres.")
        String nombre,

        @Schema(example = "+56 9 8765 4321")
        @Pattern(regexp = Validaciones.CELULAR, message = Validaciones.MENSAJE_CELULAR)
        String telefono,

        @Schema(example = "Maule")
        @Size(max = 60, message = "La región puede tener hasta 60 caracteres.")
        String region,

        @Schema(example = "Rancagua")
        @Size(max = 60, message = "La comuna puede tener hasta 60 caracteres.")
        String comuna,

        @Schema(example = "Av. Brasil 1234, depto. 5")
        @Size(max = 150, message = "La dirección puede tener hasta 150 caracteres.")
        String direccion,

        @Schema(example = "🐱")
        @Size(max = 16, message = "El ícono del avatar no es válido.")
        String avatarIcono,

        @Schema(example = "#8E44AD")
        @Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$", message = "El color debe tener el formato #RRGGBB.")
        String avatarColor) {
}
