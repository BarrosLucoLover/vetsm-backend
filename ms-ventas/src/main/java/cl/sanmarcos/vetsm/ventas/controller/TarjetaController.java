package cl.sanmarcos.vetsm.ventas.controller;

import cl.sanmarcos.vetsm.comun.web.Respuestas;
import cl.sanmarcos.vetsm.ventas.dto.TarjetaRequest;
import cl.sanmarcos.vetsm.ventas.dto.TarjetaResponse;
import cl.sanmarcos.vetsm.comun.exception.ErrorResponse;
import cl.sanmarcos.vetsm.ventas.service.TarjetaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Tarjetas guardadas del cliente que inició sesión. Nadie puede ver ni usar tarjetas ajenas. */
@RestController
@RequestMapping("/api/v1/tarjetas")
@Tag(name = "Mi cuenta")
@PreAuthorize("hasRole('CLIENTE')")
public class TarjetaController {

    private final TarjetaService tarjetas;

    public TarjetaController(TarjetaService tarjetas) {
        this.tarjetas = tarjetas;
    }

    @GetMapping
    @Operation(summary = "Mis tarjetas guardadas", description = "Rol: CLIENTE. La predeterminada primero.")
    public List<TarjetaResponse> listar(@AuthenticationPrincipal Jwt jwt) {
        return tarjetas.listar(Long.valueOf(jwt.getSubject()));
    }

    @PostMapping
    @Operation(summary = "Guarda una tarjeta", description = "Rol: CLIENTE. Solo se guardan marca, últimos 4 dígitos, titular y vencimiento; nunca el número completo ni el CVV.")
    @ApiResponse(responseCode = "201", description = "Guardada")
    @ApiResponse(responseCode = "200", description = "Ya estaba guardada")
    @ApiResponse(responseCode = "400", description = "Tarjeta inválida", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<TarjetaResponse> guardar(@AuthenticationPrincipal Jwt jwt, @RequestBody TarjetaRequest datos) {
        TarjetaService.Guardado resultado = tarjetas.guardar(Long.valueOf(jwt.getSubject()), datos);
        return resultado.duplicada()
                ? ResponseEntity.ok(resultado.tarjeta())
                : Respuestas.creado(resultado.tarjeta().id(), resultado.tarjeta());
    }

    @PatchMapping("/{id}/predeterminada")
    @Operation(summary = "Elige mi tarjeta predeterminada", description = "Rol: CLIENTE.")
    @ApiResponse(responseCode = "200", description = "Lista actualizada")
    @ApiResponse(responseCode = "404", description = "No es una tarjeta tuya", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public List<TarjetaResponse> predeterminada(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return tarjetas.marcarPredeterminada(Long.valueOf(jwt.getSubject()), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Elimina una tarjeta guardada", description = "Rol: CLIENTE.")
    @ApiResponse(responseCode = "204", description = "Eliminada")
    @ApiResponse(responseCode = "404", description = "No es una tarjeta tuya", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public void eliminar(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        tarjetas.eliminar(Long.valueOf(jwt.getSubject()), id);
    }
}
