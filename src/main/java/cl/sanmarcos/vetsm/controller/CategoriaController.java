package cl.sanmarcos.vetsm.controller;

import cl.sanmarcos.vetsm.dto.CategoriaRequest;
import cl.sanmarcos.vetsm.dto.CategoriaResponse;
import cl.sanmarcos.vetsm.exception.ErrorResponse;
import cl.sanmarcos.vetsm.service.CategoriaService;
import io.swagger.v3.oas.annotations.Operation;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categorias")
@Tag(name = "Categorías")
public class CategoriaController {

    private final CategoriaService servicio;

    public CategoriaController(CategoriaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @Operation(summary = "Lista las categorías", description = "Público.")
    public List<CategoriaResponse> listar() {
        return servicio.listar();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtiene una categoría", description = "Público.")
    @ApiResponse(responseCode = "200", description = "Categoría encontrada")
    @ApiResponse(responseCode = "404", description = "No existe", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public CategoriaResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PostMapping
    @Operation(summary = "Crea una categoría", description = "Rol: ADMIN.")
    @ApiResponse(responseCode = "201", description = "Creada. La cabecera Location indica su URL")
    @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Ya existe una con ese nombre", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<CategoriaResponse> crear(@Valid @RequestBody CategoriaRequest datos) {
        CategoriaResponse creada = servicio.crear(datos);
        return Respuestas.creado(creada.id(), creada);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edita una categoría", description = "Rol: ADMIN.")
    @ApiResponse(responseCode = "200", description = "Actualizada")
    @ApiResponse(responseCode = "404", description = "No existe", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Ya existe otra con ese nombre", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public CategoriaResponse actualizar(@PathVariable Long id, @Valid @RequestBody CategoriaRequest datos) {
        return servicio.actualizar(id, datos);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina una categoría vacía", description = "Rol: ADMIN. Si tiene productos responde 409.")
    @ApiResponse(responseCode = "204", description = "Eliminada")
    @ApiResponse(responseCode = "404", description = "No existe", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Tiene productos", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
    }
}
