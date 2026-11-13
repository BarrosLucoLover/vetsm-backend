package cl.sanmarcos.vetsm.usuarios.dto;

import cl.sanmarcos.vetsm.comun.security.Rol;
import cl.sanmarcos.vetsm.usuarios.model.Usuario;
import java.time.LocalDateTime;

/** Datos públicos de una cuenta. Nunca incluye la contraseña ni su hash. */
public record UsuarioResponse(
        Long id,
        String nombre,
        String email,
        String telefono,
        Rol rol,
        boolean activo,
        String region,
        String comuna,
        String direccion,
        String avatarIcono,
        String avatarColor,
        LocalDateTime fechaCreacion) {

    public static UsuarioResponse desde(Usuario u) {
        return new UsuarioResponse(u.getId(), u.getNombre(), u.getEmail(), u.getTelefono(), u.getRol(), u.isActivo(),
                u.getRegion(), u.getComuna(), u.getDireccion(), u.getAvatarIcono(), u.getAvatarColor(), u.getFechaCreacion());
    }
}
