package cl.sanmarcos.vetsm.dto;

import cl.sanmarcos.vetsm.model.TipoProducto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos para crear o editar un producto. Los artículos (PRODUCTO) llevan stock; los servicios llevan duración.")
public record ProductoRequest(
        @Schema(example = "Collar antipulgas")
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 120, message = "El nombre puede tener hasta 120 caracteres.")
        String nombre,

        @Schema(example = "Protección contra pulgas y garrapatas por 8 meses.")
        @Size(max = 500, message = "La descripción puede tener hasta 500 caracteres.")
        String descripcion,

        @Schema(example = "12990", description = "Precio en pesos chilenos")
        @NotNull(message = "El precio es obligatorio.")
        @Positive(message = "El precio debe ser mayor que 0.")
        @Max(value = 10_000_000, message = "El precio no puede superar $10.000.000.")
        Integer precio,

        @Schema(example = "PRODUCTO")
        @NotNull(message = "Indica si es un SERVICIO o un PRODUCTO.")
        TipoProducto tipo,

        @Schema(example = "30", description = "Obligatorio para PRODUCTO; se ignora en SERVICIO")
        @PositiveOrZero(message = "El stock no puede ser negativo.")
        Integer stock,

        @Schema(example = "30", description = "Minutos de atención. Obligatorio para SERVICIO; se ignora en PRODUCTO")
        @Positive(message = "La duración debe ser mayor que 0.")
        @Max(value = 600, message = "La duración no puede superar 600 minutos.")
        Integer duracionMin,

        @Size(max = 300, message = "La URL de la imagen puede tener hasta 300 caracteres.")
        String imagenUrl,

        @Schema(example = "3")
        @NotNull(message = "La categoría es obligatoria.")
        Long categoriaId,

        @Schema(example = "true", description = "Si se omite al crear, el producto queda activo")
        Boolean activo) {
}
