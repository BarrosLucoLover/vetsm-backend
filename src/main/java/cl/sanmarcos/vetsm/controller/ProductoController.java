package cl.sanmarcos.vetsm.controller;

import cl.sanmarcos.vetsm.dto.ProductoRequest;
import cl.sanmarcos.vetsm.dto.ProductoResponse;
import cl.sanmarcos.vetsm.exception.ErrorResponse;
import cl.sanmarcos.vetsm.model.TipoProducto;
import cl.sanmarcos.vetsm.service.ProductoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
@RequestMapping("/api/v1/productos")
@Tag(name = "Productos")
public class ProductoController {

    private final ProductoService servicio;

    public ProductoController(ProductoService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    @SecurityRequirements
    @Operation(summary = "Lista el catálogo",
            description = "Público. Filtros opcionales por categoría y tipo. Los productos dados de baja solo los ve el ADMIN.")
    public List<ProductoResponse> listar(
            @Parameter(description = "Id de la categoría", example = "1") @RequestParam(required = false) Long categoriaId,
            @Parameter(description = "SERVICIO o PRODUCTO") @RequestParam(required = false) TipoProducto tipo,
            @Parameter(description = "Incluir los dados de baja (solo ADMIN)") @RequestParam(defaultValue = "false") boolean incluirInactivos,
            Authentication autenticacion) {
        boolean esAdmin = autenticacion != null && autenticacion.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        return servicio.listar(categoriaId, tipo, incluirInactivos && esAdmin);
    }

    @GetMapping("/{id}")
    @SecurityRequirements
    @Operation(summary = "Obtiene un producto", description = "Público.")
    @ApiResponse(responseCode = "200", description = "Producto encontrado")
    @ApiResponse(responseCode = "404", description = "No existe", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ProductoResponse obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crea un producto o servicio", description = "Rol: ADMIN. Los PRODUCTO requieren stock; los SERVICIO, duración.")
    @ApiResponse(responseCode = "201", description = "Creado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "La categoría no existe", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoRequest datos) {
        ProductoResponse creado = servicio.crear(datos);
        return Respuestas.creado(creado.id(), creado);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Edita un producto (precio, stock, categoría...)", description = "Rol: ADMIN.")
    @ApiResponse(responseCode = "200", description = "Actualizado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "No existe", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ProductoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest datos) {
        return servicio.actualizar(id, datos);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Da de baja un producto",
            description = "Rol: ADMIN. Baja lógica: deja de venderse pero se conserva en las boletas y citas anteriores.")
    @ApiResponse(responseCode = "204", description = "Dado de baja")
    @ApiResponse(responseCode = "404", description = "No existe", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void darDeBaja(@PathVariable Long id) {
        servicio.darDeBaja(id);
    }
}
