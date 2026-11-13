package cl.sanmarcos.vetsm.agenda.controller;

import cl.sanmarcos.vetsm.comun.web.Respuestas;
import cl.sanmarcos.vetsm.agenda.dto.CambioEstadoCitaRequest;
import cl.sanmarcos.vetsm.agenda.dto.CitaRequest;
import cl.sanmarcos.vetsm.agenda.dto.CitaResponse;
import cl.sanmarcos.vetsm.comun.exception.ErrorResponse;
import cl.sanmarcos.vetsm.agenda.model.EstadoCita;
import cl.sanmarcos.vetsm.comun.security.Actor;
import cl.sanmarcos.vetsm.agenda.service.CitaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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

/** Agenda: cualquiera solicita una cita; el personal la gestiona (RF3, RF4); cada cliente ve las suyas. */
@RestController
@RequestMapping("/api/v1/citas")
@Tag(name = "Citas")
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPCIONISTA')")
public class CitaController {

    private final CitaService servicio;

    public CitaController(CitaService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @PreAuthorize("permitAll()")
    @Operation(summary = "Agenda una cita (queda PENDIENTE)", description = """
            Público: también la usa el formulario de la portada, sin sesión. Con sesión de CLIENTE se puede
            indicar mascotaId (solo de sus propias mascotas). No se agenda en el pasado ni en un horario ocupado.""")
    @ApiResponse(responseCode = "201", description = "Agendada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Horario ocupado", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "Fecha pasada o servicio no disponible", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<CitaResponse> crear(@Valid @RequestBody CitaRequest datos, @AuthenticationPrincipal Jwt jwt) {
        CitaResponse creada = servicio.crear(datos, Actor.desde(jwt));
        return Respuestas.creado(creada.id(), creada);
    }

    @GetMapping("/mias")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "Citas de mis mascotas", description = "Rol: CLIENTE.")
    public List<CitaResponse> mias(@AuthenticationPrincipal Jwt jwt) {
        return servicio.listarDeCliente(Long.valueOf(jwt.getSubject()));
    }

    @GetMapping
    @Operation(summary = "Lista la agenda", description = "Roles: ADMIN y RECEPCIONISTA. Ordenada por fecha y hora; filtros opcionales.")
    public List<CitaResponse> listar(
            @Parameter(description = "Estado de la cita") @RequestParam(required = false) EstadoCita estado,
            @Parameter(description = "Día (AAAA-MM-DD)", example = "2026-12-01")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return servicio.listar(estado, fecha);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene una cita", description = "Roles: ADMIN y RECEPCIONISTA.")
    @ApiResponse(responseCode = "200", description = "Cita encontrada")
    @ApiResponse(responseCode = "404", description = "No existe", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public CitaResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita o reagenda una cita", description = "Roles: ADMIN y RECEPCIONISTA. No se modifican citas completadas ni canceladas.")
    @ApiResponse(responseCode = "200", description = "Actualizada")
    @ApiResponse(responseCode = "409", description = "Horario ocupado o cita ya terminada", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public CitaResponse actualizar(@PathVariable Long id, @Valid @RequestBody CitaRequest datos, @AuthenticationPrincipal Jwt jwt) {
        return servicio.actualizar(id, datos, Actor.desde(jwt));
    }

    @PatchMapping("/{id}/estado")
    @Operation(summary = "Cambia el estado (confirmar, completar o cancelar)",
            description = "Roles: ADMIN y RECEPCIONISTA. Transiciones: PENDIENTE→CONFIRMADA→COMPLETADA, y CANCELADA antes de completar.")
    @ApiResponse(responseCode = "200", description = "Estado cambiado")
    @ApiResponse(responseCode = "409", description = "Transición no permitida", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public CitaResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody CambioEstadoCitaRequest datos) {
        return servicio.cambiarEstado(id, datos.estado());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina una cita", description = "Roles: ADMIN y RECEPCIONISTA. Las completadas no se eliminan (historial clínico).")
    @ApiResponse(responseCode = "204", description = "Eliminada")
    @ApiResponse(responseCode = "409", description = "La cita está completada", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
    }
}
