package cl.sanmarcos.vetsm.controller;

import cl.sanmarcos.vetsm.dto.MascotaRequest;
import cl.sanmarcos.vetsm.dto.MascotaResponse;
import cl.sanmarcos.vetsm.exception.ErrorResponse;
import cl.sanmarcos.vetsm.service.MascotaService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mascotas")
@Tag(name = "Mascotas")
public class MascotaController {

    private final MascotaService servicio;

    public MascotaController(MascotaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Lista las mascotas", description = "Roles: ADMIN y RECEPCIONISTA. Filtro opcional por dueño.")
    public List<MascotaResponse> listar(@Parameter(description = "Id del dueño", example = "3") @RequestParam(required = false) Long duenoId) {
        return servicio.listar(duenoId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene una mascota", description = "Roles: ADMIN y RECEPCIONISTA.")
    @ApiResponse(responseCode = "200", description = "Mascota encontrada")
    @ApiResponse(responseCode = "404", description = "No existe", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public MascotaResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PostMapping
    @Operation(summary = "Registra una mascota", description = "Roles: ADMIN y RECEPCIONISTA. El dueño debe ser un CLIENTE.")
    @ApiResponse(responseCode = "201", description = "Registrada")
    @ApiResponse(responseCode = "422", description = "El dueño no es un cliente", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<MascotaResponse> crear(@Valid @RequestBody MascotaRequest datos) {
        MascotaResponse creada = servicio.crear(datos);
        return Respuestas.creado(creada.id(), creada);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita una mascota", description = "Roles: ADMIN y RECEPCIONISTA.")
    @ApiResponse(responseCode = "200", description = "Actualizada")
    @ApiResponse(responseCode = "404", description = "No existe", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public MascotaResponse actualizar(@PathVariable Long id, @Valid @RequestBody MascotaRequest datos) {
        return servicio.actualizar(id, datos);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina una mascota sin citas", description = "Roles: ADMIN y RECEPCIONISTA.")
    @ApiResponse(responseCode = "204", description = "Eliminada")
    @ApiResponse(responseCode = "409", description = "Tiene citas registradas", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
    }
}
