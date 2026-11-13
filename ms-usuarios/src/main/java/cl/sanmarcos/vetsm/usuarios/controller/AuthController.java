package cl.sanmarcos.vetsm.usuarios.controller;

import cl.sanmarcos.vetsm.usuarios.dto.LoginRequest;
import cl.sanmarcos.vetsm.usuarios.dto.RegistroRequest;
import cl.sanmarcos.vetsm.usuarios.dto.SesionResponse;
import cl.sanmarcos.vetsm.comun.exception.ErrorResponse;
import cl.sanmarcos.vetsm.usuarios.security.CookieSesion;
import cl.sanmarcos.vetsm.usuarios.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sesión del usuario. Al iniciar sesión se entregan dos tokens:
 * - El de acceso (JWT) en el cuerpo: el frontend lo envía en "Authorization: Bearer ...".
 * - El de renovación en una cookie HttpOnly: sirve para pedir un token de acceso nuevo cuando
 *   el anterior vence (o al recargar la página) sin volver a escribir la contraseña.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Autenticación")
@SecurityRequirements
public class AuthController {

    private final AuthService servicio;
    private final CookieSesion cookie;

    public AuthController(AuthService servicio, CookieSesion cookie) {
        this.servicio = servicio;
        this.cookie = cookie;
    }

    @PostMapping("/login")
    @Operation(summary = "Inicia sesión", description = """
            Público. Devuelve el token de acceso (JWT) y deja el token de renovación en una cookie HttpOnly.
            Cuentas de demostración (perfil h2): admin@sanmarcos.cl / Admin123!, recepcion@sanmarcos.cl / Recep123!,
            ana.garcia@correo.cl / Cliente123!. Copia el accessToken y pégalo en el botón "Authorize".""")
    @ApiResponse(responseCode = "200", description = "Sesión iniciada")
    @ApiResponse(responseCode = "401", description = "Credenciales incorrectas o cuenta desactivada", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<SesionResponse> login(@Valid @RequestBody LoginRequest datos) {
        return conCookie(HttpStatus.OK, servicio.iniciarSesion(datos));
    }

    @PostMapping("/registro")
    @Operation(summary = "Crea una cuenta de cliente e inicia sesión", description = "Público. El rol siempre es CLIENTE.")
    @ApiResponse(responseCode = "201", description = "Cuenta creada y sesión iniciada")
    @ApiResponse(responseCode = "400", description = "Datos inválidos", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "El correo ya está registrado", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<SesionResponse> registro(@Valid @RequestBody RegistroRequest datos) {
        return conCookie(HttpStatus.CREATED, servicio.registrar(datos));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renueva el token de acceso", description = """
            Usa la cookie de renovación (el navegador la envía sola). Cada renovación entrega un token de acceso
            nuevo y cambia la cookie por una nueva (rotación). Un token ya usado no vuelve a servir.""")
    @ApiResponse(responseCode = "200", description = "Tokens renovados")
    @ApiResponse(responseCode = "401", description = "No hay sesión o ya no es válida", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<SesionResponse> refresh(@CookieValue(name = CookieSesion.NOMBRE, required = false) String tokenRenovacion) {
        return conCookie(HttpStatus.OK, servicio.renovar(tokenRenovacion));
    }

    @PostMapping("/logout")
    @Operation(summary = "Cierra la sesión", description = "Revoca el token de renovación y borra la cookie.")
    @ApiResponse(responseCode = "204", description = "Sesión cerrada")
    public ResponseEntity<Void> logout(@CookieValue(name = CookieSesion.NOMBRE, required = false) String tokenRenovacion) {
        servicio.cerrarSesion(tokenRenovacion);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.borrar().toString()).build();
    }

    private ResponseEntity<SesionResponse> conCookie(HttpStatus estado, AuthService.Sesion sesion) {
        String valor = cookie.crear(sesion.tokens().renovacion(), sesion.tokens().duracionRenovacion()).toString();
        return ResponseEntity.status(estado).header(HttpHeaders.SET_COOKIE, valor).body(sesion.respuesta());
    }
}
