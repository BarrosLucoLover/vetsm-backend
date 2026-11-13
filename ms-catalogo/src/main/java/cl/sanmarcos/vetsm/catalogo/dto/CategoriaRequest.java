package cl.sanmarcos.vetsm.catalogo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para crear o editar una categoría")
public record CategoriaRequest(
        @Schema(example = "Accesorios")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 60, message = "El nombre puede tener hasta 60 caracteres.")
        String nombre,

        @Schema(example = "Collares, juguetes y artículos de paseo.")
        @Size(max = 255, message = "La descripción puede tener hasta 255 caracteres.")
        String descripcion) {
}
