package cl.sanmarcos.vetsm.comun.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Quién hace la petición, leído del token JWT ya verificado por Spring Security.
 * Los servicios lo usan para decidir sobre datos propios (por ejemplo, "solo tus tarjetas") y para
 * copiar el nombre y el correo del cliente sin tener que preguntarle a ms-usuarios.
 */
public record Actor(Long id, Rol rol, String nombre, String email) {

    public static Actor desde(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        return new Actor(Long.valueOf(jwt.getSubject()), Rol.valueOf(jwt.getClaimAsString(JwtConfig.CLAIM_ROL)),
                jwt.getClaimAsString(JwtConfig.CLAIM_NOMBRE), jwt.getClaimAsString(JwtConfig.CLAIM_EMAIL));
    }

    /** Personal de la clínica: administrador o recepcionista. */
    public boolean esPersonal() {
        return rol == Rol.ADMIN || rol == Rol.RECEPCIONISTA;
    }

    public boolean esAdmin() {
        return rol == Rol.ADMIN;
    }
}
