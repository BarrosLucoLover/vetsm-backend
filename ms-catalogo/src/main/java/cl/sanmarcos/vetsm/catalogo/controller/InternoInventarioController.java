package cl.sanmarcos.vetsm.catalogo.controller;

import cl.sanmarcos.vetsm.catalogo.dto.MovimientoStockRequest;
import cl.sanmarcos.vetsm.catalogo.dto.ReservaResponse;
import cl.sanmarcos.vetsm.catalogo.service.InventarioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints internos del inventario: solo los llama ms-ventas con un token de servicio (rol SERVICIO).
 * El gateway no los publica, así que nadie desde el navegador puede cambiar el stock.
 */
@RestController
@RequestMapping("/internal/v1/inventario")
@PreAuthorize("hasRole('SERVICIO')")
public class InternoInventarioController {

    private final InventarioService inventario;

    public InternoInventarioController(InventarioService inventario) {
        this.inventario = inventario;
    }

    /** Paso 1 de una compra: reserva el stock y entrega los precios actuales. */
    @PostMapping("/reservas")
    public ReservaResponse reservar(@Valid @RequestBody MovimientoStockRequest pedido) {
        return inventario.reservar(pedido);
    }

    /** Compensación: devuelve el stock si el pago falló o si se anuló la compra. */
    @PostMapping("/devoluciones")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void devolver(@Valid @RequestBody MovimientoStockRequest pedido) {
        inventario.devolver(pedido);
    }
}
