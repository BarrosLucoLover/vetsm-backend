package cl.sanmarcos.vetsm.catalogo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Productos y cantidades que ms-ventas pide reservar (al comprar) o devolver al stock (si el pago
 * falla o se anula una compra). Lo usan solo los endpoints internos.
 */
public record MovimientoStockRequest(@NotEmpty List<@Valid Item> items) {

    public record Item(@NotNull Long productoId, @NotNull @Min(1) Integer cantidad) {
    }
}
