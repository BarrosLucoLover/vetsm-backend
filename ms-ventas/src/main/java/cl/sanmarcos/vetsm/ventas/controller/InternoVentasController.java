package cl.sanmarcos.vetsm.ventas.controller;

import cl.sanmarcos.vetsm.ventas.service.BoletaService;
import cl.sanmarcos.vetsm.ventas.service.TarjetaService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints internos que usa ms-usuarios al eliminar una cuenta (solo rol SERVICIO; el gateway no los publica). */
@RestController
@RequestMapping("/internal/v1/clientes")
@PreAuthorize("hasRole('SERVICIO')")
public class InternoVentasController {

    public record Referencias(long cantidad, String detalle) {
    }

    private final BoletaService boletas;
    private final TarjetaService tarjetas;

    public InternoVentasController(BoletaService boletas, TarjetaService tarjetas) {
        this.boletas = boletas;
        this.tarjetas = tarjetas;
    }

    /** Cuántas compras tiene el cliente: si tiene, su cuenta no se puede eliminar (solo desactivar). */
    @GetMapping("/{id}/referencias")
    public Referencias referencias(@PathVariable Long id) {
        long compras = boletas.contarDeCliente(id);
        return new Referencias(compras, compras + " compras");
    }

    @DeleteMapping("/{id}/tarjetas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarTarjetas(@PathVariable Long id) {
        tarjetas.eliminarTodas(id);
    }
}
