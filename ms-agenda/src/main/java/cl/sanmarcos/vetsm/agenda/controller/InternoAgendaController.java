package cl.sanmarcos.vetsm.agenda.controller;

import cl.sanmarcos.vetsm.agenda.service.MascotaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint interno que usa ms-usuarios al eliminar una cuenta (solo rol SERVICIO; el gateway no lo publica). */
@RestController
@RequestMapping("/internal/v1/clientes")
@PreAuthorize("hasRole('SERVICIO')")
public class InternoAgendaController {

    public record Referencias(long cantidad, String detalle) {
    }

    private final MascotaService mascotas;

    public InternoAgendaController(MascotaService mascotas) {
        this.mascotas = mascotas;
    }

    /** Cuántas mascotas tiene el cliente: si tiene, su cuenta no se puede eliminar (solo desactivar). */
    @GetMapping("/{id}/referencias")
    public Referencias referencias(@PathVariable Long id) {
        long cantidad = mascotas.contarDeDueno(id);
        return new Referencias(cantidad, cantidad + " mascotas");
    }
}
