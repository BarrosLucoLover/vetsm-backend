package cl.sanmarcos.vetsm.controller;

import cl.sanmarcos.vetsm.dto.EstadoActivoRequest;
import cl.sanmarcos.vetsm.dto.UsuarioRequest;
import cl.sanmarcos.vetsm.dto.UsuarioResponse;
import cl.sanmarcos.vetsm.dto.UsuarioUpdateRequest;
import cl.sanmarcos.vetsm.exception.ErrorResponse;
import cl.sanmarcos.vetsm.model.Rol;
import cl.sanmarcos.vetsm.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gestión de cuentas: solo el administrador (RF7). */
@RestController
@RequestMapping("/api/v1/usuarios")
@Tag(name = "Usuarios")
@PreAuthorize("hasRole('ADMIN')")
public class UsuarioController {

    private final UsuarioService servicio;

    public UsuarioController(UsuarioService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Lista las cuentas", description = "Rol: ADMIN. Filtro opcional por rol.")
    public List<UsuarioResponse> listar(@Parameter(description = "ADMIN, RECEPCIONISTA o CLIENTE") @RequestParam(required = false) Rol rol) {
        return servicio.listar(rol);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene una cuenta", description = "Rol: ADMIN.")
    @ApiResponse(responseCode = "200", description = "Cuenta encontrada")
    @ApiResponse(responseCode = "404", description = "No existe", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public UsuarioResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PostMapping
    @Operation(summary = "Crea una cuenta de cualquier rol", description = "Rol: ADMIN. La contraseña se guarda con hash BCrypt.")
    @ApiResponse(responseCode = "201", description = "Creada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "El correo ya está registrado", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody UsuarioRequest datos) {
        UsuarioResponse creado = servicio.crear(datos);
        return Respuestas.creado(creado.id(), creado);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita nombre, correo, teléfono y rol",
            description = "Rol: ADMIN. No se puede quitar el rol al único administrador ni a uno mismo.")
    @ApiResponse(responseCode = "200", description = "Actualizada")
    @ApiResponse(responseCode = "409", description = "El correo ya está registrado", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "Dejaría el sistema sin administrador", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public UsuarioResponse actualizar(@PathVariable Long id, @Valid @RequestBody UsuarioUpdateRequest datos, @AuthenticationPrincipal Jwt jwt) {
        return servicio.actualizar(id, datos, Long.valueOf(jwt.getSubject()));
    }

    @PatchMapping("/{id}/activo")
    @Operation(summary = "Activa o desactiva una cuenta",
            description = "Rol: ADMIN. Una cuenta inactiva no puede iniciar sesión y se cierran sus sesiones. Nadie puede desactivarse a sí mismo.")
    @ApiResponse(responseCode = "200", description = "Estado cambiado")
    @ApiResponse(responseCode = "422", description = "Es la propia cuenta o el único administrador", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public UsuarioResponse cambiarActivo(@PathVariable Long id, @Valid @RequestBody EstadoActivoRequest datos, @AuthenticationPrincipal Jwt jwt) {
        return servicio.cambiarActivo(id, datos.activo(), Long.valueOf(jwt.getSubject()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina una cuenta sin historial", description = "Rol: ADMIN. Si tiene compras o mascotas responde 409: hay que desactivarla.")
    @ApiResponse(responseCode = "204", description = "Eliminada")
    @ApiResponse(responseCode = "404", description = "No existe", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Tiene historial", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "Es la propia cuenta o el único administrador", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void eliminar(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        servicio.eliminar(id, Long.valueOf(jwt.getSubject()));
    }
}
