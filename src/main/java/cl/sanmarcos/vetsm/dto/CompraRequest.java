package cl.sanmarcos.vetsm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Compra del carrito. No lleva el cliente ni los precios: el cliente se toma del token JWT (nadie
 * puede comprar a nombre de otro) y los precios, de la base de datos (nadie puede cambiarlos).
 */
@Schema(description = "Compra del carrito. El cliente se toma del token y los precios de la base de datos.")
public record CompraRequest(
        @NotEmpty(message = "El carrito está vacío.")
        @Size(max = 50, message = "El carrito puede tener hasta 50 productos distintos.")
        List<@Valid ItemCompraRequest> items,

        @NotNull(message = "Indica el medio de pago.")
        @Valid
        PagoRequest pago) {
}
