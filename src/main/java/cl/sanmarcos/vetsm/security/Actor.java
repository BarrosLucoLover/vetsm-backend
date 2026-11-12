package cl.sanmarcos.vetsm.security;

import cl.sanmarcos.vetsm.model.Rol;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Quién hace la petición, leído del token JWT ya verificado por Spring Security.
 * Los servicios lo usan para decidir sobre datos propios (por ejemplo, "solo tus tarjetas").
 */
public record Actor(Long id, Rol rol) {

    public static Actor desde(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        return new Actor(Long.valueOf(jwt.getSubject()), Rol.valueOf(jwt.getClaimAsString(TokenService.CLAIM_ROL)));
    }

    /** Personal de la clínica: administrador o recepcionista. */
    public boolean esPersonal() {
        return rol == Rol.ADMIN || rol == Rol.RECEPCIONISTA;
    }

    public boolean esAdmin() {
        return rol == Rol.ADMIN;
    }
}
