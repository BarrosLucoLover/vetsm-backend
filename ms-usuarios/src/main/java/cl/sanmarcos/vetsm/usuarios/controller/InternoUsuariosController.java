package cl.sanmarcos.vetsm.usuarios.controller;

import cl.sanmarcos.vetsm.usuarios.dto.UsuarioResponse;
import cl.sanmarcos.vetsm.usuarios.service.UsuarioService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints internos: solo los llaman otros microservicios (rol SERVICIO). El gateway no los publica
 * (solo enruta /api/v1/...), así que desde internet no se pueden alcanzar.
 */
@RestController
@RequestMapping("/internal/v1/usuarios")
@PreAuthorize("hasRole('SERVICIO')")
public class InternoUsuariosController {

    private final UsuarioService usuarios;

    public InternoUsuariosController(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    /** Lo usa ms-agenda para registrar una mascota a nombre de un cliente y copiar su nombre y teléfono. */
    @GetMapping("/{id}")
    public UsuarioResponse obtener(@PathVariable Long id) {
        return usuarios.obtener(id);
    }
}
