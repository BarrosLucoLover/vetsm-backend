package cl.sanmarcos.vetsm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@Schema(description = "Compra del carrito. Los precios NO se envían: el servidor los toma de la base de datos.")
public record CompraRequest(
        @Schema(example = "3", description = "Cliente que compra. Desde la fase 3 se toma de la sesión (token JWT)")
        @NotNull(message = "Indica el cliente.")
        Long clienteId,

        @NotEmpty(message = "El carrito está vacío.")
        @Size(max = 50, message = "El carrito puede tener hasta 50 productos distintos.")
        List<@Valid ItemCompraRequest> items,

        @NotNull(message = "Indica el medio de pago.")
        @Valid
        PagoRequest pago) {
}
