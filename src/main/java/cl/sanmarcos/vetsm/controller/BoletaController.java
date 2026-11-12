package cl.sanmarcos.vetsm.controller;

import cl.sanmarcos.vetsm.dto.BoletaResponse;
import cl.sanmarcos.vetsm.dto.CompraRequest;
import cl.sanmarcos.vetsm.exception.ErrorResponse;
import cl.sanmarcos.vetsm.security.Actor;
import cl.sanmarcos.vetsm.service.BoletaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Compras: el cliente compra y ve las suyas; el administrador ve todas y puede anular. */
@RestController
@RequestMapping("/api/v1/boletas")
@Tag(name = "Boletas")
public class BoletaController {

    private final BoletaService servicio;

    public BoletaController(BoletaService servicio) {
        this.servicio = servicio;
    }

    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "Compra el carrito y paga con tarjeta (pasarela simulada)", description = """
            Rol: CLIENTE. El cliente se toma del token y los precios de la base de datos; se descuenta el stock.
            Tarjetas de prueba: 4111 1111 1111 1111 (aprobada) y 4000 0000 0000 0002 (rechazada), con vencimiento futuro.""")
    @ApiResponse(responseCode = "201", description = "Compra pagada; devuelve la boleta")
    @ApiResponse(responseCode = "400", description = "Datos o tarjeta inválidos", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "402", description = "El banco rechazó la tarjeta", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Stock insuficiente", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "422", description = "Producto no disponible o tarjeta vencida", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<BoletaResponse> comprar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CompraRequest compra) {
        BoletaResponse boleta = servicio.comprar(Long.valueOf(jwt.getSubject()), compra);
        return Respuestas.creado(boleta.id(), boleta);
    }

    @GetMapping("/mias")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "Mis compras", description = "Rol: CLIENTE. De la más reciente a la más antigua.")
    public List<BoletaResponse> mias(@AuthenticationPrincipal Jwt jwt) {
        return servicio.listarDeCliente(Long.valueOf(jwt.getSubject()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Lista todas las compras", description = "Rol: ADMIN.")
    public List<BoletaResponse> listar() {
        return servicio.listar();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CLIENTE')")
    @Operation(summary = "Obtiene una boleta con su detalle", description = "Roles: ADMIN (cualquiera) y CLIENTE (solo las suyas).")
    @ApiResponse(responseCode = "200", description = "Boleta encontrada")
    @ApiResponse(responseCode = "404", description = "No existe o no es tuya", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public BoletaResponse obtener(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return servicio.obtener(id, Actor.desde(jwt));
    }

    @PatchMapping("/{id}/anular")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Anula una compra y devuelve el stock", description = "Rol: ADMIN.")
    @ApiResponse(responseCode = "200", description = "Anulada")
    @ApiResponse(responseCode = "409", description = "Ya estaba anulada", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public BoletaResponse anular(@PathVariable Long id) {
        return servicio.anular(id);
    }
}
