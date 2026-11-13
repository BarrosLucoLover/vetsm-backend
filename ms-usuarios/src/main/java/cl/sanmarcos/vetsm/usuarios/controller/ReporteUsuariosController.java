package cl.sanmarcos.vetsm.usuarios.controller;

import cl.sanmarcos.vetsm.usuarios.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Métricas de cuentas para el panel de administración. */
@RestController
@RequestMapping("/api/v1/reportes/usuarios")
@Tag(name = "Reportes")
@PreAuthorize("hasRole('ADMIN')")
public class ReporteUsuariosController {

    public record ReporteUsuarios(long clientesActivos) {
    }

    private final UsuarioService usuarios;

    public ReporteUsuariosController(UsuarioService usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping
    @Operation(summary = "Clientes activos", description = "Rol: ADMIN.")
    public ReporteUsuarios resumen() {
        return new ReporteUsuarios(usuarios.contarClientesActivos());
    }
}
