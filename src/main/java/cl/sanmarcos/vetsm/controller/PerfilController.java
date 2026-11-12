package cl.sanmarcos.vetsm.controller;

import cl.sanmarcos.vetsm.dto.CambioPasswordRequest;
import cl.sanmarcos.vetsm.dto.PerfilRequest;
import cl.sanmarcos.vetsm.dto.UsuarioResponse;
import cl.sanmarcos.vetsm.exception.ErrorResponse;
import cl.sanmarcos.vetsm.security.CookieSesion;
import cl.sanmarcos.vetsm.security.TokenService;
import cl.sanmarcos.vetsm.service.AuthService;
import cl.sanmarcos.vetsm.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Mi cuenta": cada usuario ve y edita solo su propio perfil (el id sale del token, no de la URL). */
@RestController
@RequestMapping("/api/v1/perfil")
@Tag(name = "Mi cuenta")
public class PerfilController {

    private final UsuarioService usuarios;
    private final AuthService auth;
    private final CookieSesion cookie;

    public PerfilController(UsuarioService usuarios, AuthService auth, CookieSesion cookie) {
        this.usuarios = usuarios;
        this.auth = auth;
        this.cookie = cookie;
    }

    @GetMapping
    @Operation(summary = "Mis datos", description = "Cualquier usuario con sesión. Lo usa el frontend para saber quién inició sesión.")
    public UsuarioResponse obtener(@AuthenticationPrincipal Jwt jwt) {
        return usuarios.obtener(Long.valueOf(jwt.getSubject()));
    }

    @PutMapping
    @Operation(summary = "Personaliza mi perfil", description = "Nombre, teléfono, región, comuna, dirección y avatar. No cambia el correo ni el rol.")
    @ApiResponse(responseCode = "200", description = "Perfil actualizado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public UsuarioResponse actualizar(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PerfilRequest datos) {
        return usuarios.actualizarPerfil(Long.valueOf(jwt.getSubject()), datos);
    }

    @PutMapping("/password")
    @Operation(summary = "Cambia mi contraseña",
            description = "Exige la contraseña actual. Cierra las sesiones de los demás dispositivos y renueva la de este.")
    @ApiResponse(responseCode = "204", description = "Contraseña cambiada")
    @ApiResponse(responseCode = "400", description = "La contraseña actual no es correcta o la nueva no es válida", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<Void> cambiarPassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CambioPasswordRequest datos) {
        Long id = Long.valueOf(jwt.getSubject());
        usuarios.cambiarPassword(id, datos);
        TokenService.Tokens nuevos = auth.reiniciarSesiones(id);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.crear(nuevos.renovacion(), nuevos.duracionRenovacion()).toString())
                .build();
    }
}
