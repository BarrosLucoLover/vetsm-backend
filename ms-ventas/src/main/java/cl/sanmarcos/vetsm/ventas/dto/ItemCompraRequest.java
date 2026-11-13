package cl.sanmarcos.vetsm.ventas.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ItemCompraRequest(
        @Schema(example = "9")
        @NotNull(message = "Indica el producto.")
        Long productoId,

        @Schema(example = "2")
        @NotNull(message = "Indica la cantidad.")
        @Min(value = 1, message = "La cantidad mínima es 1.")
        @Max(value = 99, message = "La cantidad máxima por producto es 99.")
        Integer cantidad) {
}
