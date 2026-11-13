package cl.sanmarcos.vetsm.usuarios.service;

import cl.sanmarcos.vetsm.comun.util.Textos;
import cl.sanmarcos.vetsm.usuarios.cliente.ReferenciasCliente;
import cl.sanmarcos.vetsm.usuarios.dto.CambioPasswordRequest;
import cl.sanmarcos.vetsm.usuarios.dto.PerfilRequest;
import cl.sanmarcos.vetsm.usuarios.dto.UsuarioRequest;
import cl.sanmarcos.vetsm.usuarios.dto.UsuarioResponse;
import cl.sanmarcos.vetsm.usuarios.dto.UsuarioUpdateRequest;
import cl.sanmarcos.vetsm.comun.exception.ApiException;
import cl.sanmarcos.vetsm.comun.security.Rol;
import cl.sanmarcos.vetsm.usuarios.model.Usuario;
import cl.sanmarcos.vetsm.usuarios.repository.RefreshTokenRepository;
import cl.sanmarcos.vetsm.usuarios.repository.UsuarioRepository;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final ReferenciasCliente referencias;

    public UsuarioService(UsuarioRepository usuarios, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
            ReferenciasCliente referencias) {
        this.usuarios = usuarios;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.referencias = referencias;
    }

    @Transactional(readOnly = true)
    public List<UsuarioResponse> listar(Rol rol) {
        List<Usuario> lista = rol == null ? usuarios.findAllByOrderByNombreAsc() : usuarios.findByRolOrderByNombreAsc(rol);
        return lista.stream().map(UsuarioResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtener(Long id) {
        return UsuarioResponse.desde(buscar(id));
    }

    /** Un administrador crea una cuenta de cualquier rol. La contraseña se guarda con hash BCrypt. */
    public UsuarioResponse crear(UsuarioRequest datos) {
        Usuario nuevo = new Usuario(datos.nombre().trim(), correoDisponible(datos.email(), null),
                passwordEncoder.encode(datos.password()), Textos.limpiar(datos.telefono()), datos.rol());
        return UsuarioResponse.desde(usuarios.save(nuevo));
    }

    /** Registro público: siempre crea un CLIENTE. Nadie puede darse a sí mismo un rol del personal. */
    public UsuarioResponse registrarCliente(String nombre, String email, String password) {
        Usuario nuevo = new Usuario(nombre.trim(), correoDisponible(email, null), passwordEncoder.encode(password), null, Rol.CLIENTE);
        return UsuarioResponse.desde(usuarios.save(nuevo));
    }

    /** actorId: el administrador que hace el cambio (no puede quitarse a sí mismo el rol). */
    public UsuarioResponse actualizar(Long id, UsuarioUpdateRequest datos, Long actorId) {
        Usuario usuario = buscar(id);
        if (usuario.getRol() == Rol.ADMIN && datos.rol() != Rol.ADMIN) {
            if (id.equals(actorId)) {
                throw new ApiException.ReglaNegocio("No puedes quitarte a ti mismo el rol de administrador.");
            }
            exigirOtroAdministradorActivo(usuario, "quitarle el rol de administrador");
        }
        usuario.setNombre(datos.nombre().trim());
        usuario.setEmail(correoDisponible(datos.email(), id));
        usuario.setTelefono(Textos.limpiar(datos.telefono()));
        usuario.setRol(datos.rol());
        return UsuarioResponse.desde(usuario);
    }

    /** Activa o desactiva una cuenta. Una cuenta inactiva no puede iniciar sesión, pero conserva su historial. */
    public UsuarioResponse cambiarActivo(Long id, boolean activo, Long actorId) {
        Usuario usuario = buscar(id);
        if (!activo && id.equals(actorId)) {
            throw new ApiException.ReglaNegocio("No puedes desactivar tu propia cuenta.");
        }
        if (!activo && usuario.getRol() == Rol.ADMIN && usuario.isActivo()) {
            exigirOtroAdministradorActivo(usuario, "desactivarlo");
        }
        usuario.setActivo(activo);
        if (!activo) {
            // Al desactivar, se cierran sus sesiones: ya no podrá renovar su token.
            refreshTokens.findByUsuarioIdAndRevocadoFalse(id).forEach(t -> t.setRevocado(true));
        }
        return UsuarioResponse.desde(usuario);
    }

    /**
     * Elimina una cuenta sin historial. Si el usuario ya compró o tiene mascotas registradas, se debe
     * desactivar en vez de eliminar, para no perder boletas ni fichas.
     *
     * Las compras están en ms-ventas y las mascotas en ms-agenda: se les pregunta por su API interna.
     * Si alguno no responde, no se elimina nada (es más seguro rechazar que dejar datos huérfanos).
     */
    public void eliminar(Long id, Long actorId) {
        Usuario usuario = buscar(id);
        if (id.equals(actorId)) {
            throw new ApiException.ReglaNegocio("No puedes eliminar tu propia cuenta.");
        }
        if (usuario.getRol() == Rol.ADMIN && usuario.isActivo()) {
            exigirOtroAdministradorActivo(usuario, "eliminarlo");
        }
        if (referencias.enVentas(id).cantidad() > 0 || referencias.enAgenda(id).cantidad() > 0) {
            throw new ApiException.Conflicto("La cuenta de " + usuario.getNombre()
                    + " tiene compras o mascotas registradas. Desactívala en lugar de eliminarla.");
        }
        referencias.eliminarTarjetas(id);
        refreshTokens.deleteAll(refreshTokens.findByUsuarioId(id));
        usuarios.delete(usuario);
    }

    /** "Mi cuenta": el usuario personaliza su perfil. No puede cambiar su correo ni su rol. */
    public UsuarioResponse actualizarPerfil(Long id, PerfilRequest datos) {
        Usuario usuario = buscar(id);
        usuario.setNombre(datos.nombre().trim());
        usuario.setTelefono(Textos.limpiar(datos.telefono()));
        usuario.setRegion(Textos.limpiar(datos.region()));
        usuario.setComuna(Textos.limpiar(datos.comuna()));
        usuario.setDireccion(Textos.limpiar(datos.direccion()));
        usuario.setAvatarIcono(Textos.limpiar(datos.avatarIcono()));
        usuario.setAvatarColor(Textos.limpiar(datos.avatarColor()));
        return UsuarioResponse.desde(usuario);
    }

    /** Cambia la contraseña confirmando la actual, para que nadie la cambie desde una sesión olvidada abierta. */
    public void cambiarPassword(Long id, CambioPasswordRequest datos) {
        Usuario usuario = buscar(id);
        if (!passwordEncoder.matches(datos.actual(), usuario.getPasswordHash())) {
            throw new ApiException.DatosInvalidos(Map.of("actual", "La contraseña actual no es correcta."));
        }
        if (datos.actual().equals(datos.nueva())) {
            throw new ApiException.DatosInvalidos(Map.of("nueva", "La nueva contraseña debe ser distinta de la actual."));
        }
        usuario.setPasswordHash(passwordEncoder.encode(datos.nueva()));
    }

    @Transactional(readOnly = true)
    public long contarClientesActivos() {
        return usuarios.countByRolAndActivoTrue(Rol.CLIENTE);
    }

    public Usuario buscar(Long id) {
        return usuarios.findById(id)
                .orElseThrow(() -> new ApiException.NoEncontrado("No existe el usuario " + id + "."));
    }

    /** Normaliza el correo y verifica que no lo use otra cuenta (idActual: la cuenta que se está editando). */
    private String correoDisponible(String email, Long idActual) {
        String normalizado = email.trim().toLowerCase();
        usuarios.findByEmail(normalizado)
                .filter(otro -> !otro.getId().equals(idActual))
                .ifPresent(otro -> {
                    throw new ApiException.Conflicto("Ya existe una cuenta con el correo " + normalizado + ".");
                });
        return normalizado;
    }

    /** El sistema nunca puede quedar sin un administrador activo: nadie podría gestionar las cuentas. */
    private void exigirOtroAdministradorActivo(Usuario admin, String accion) {
        if (usuarios.countByRolAndActivoTrue(Rol.ADMIN) <= 1) {
            throw new ApiException.ReglaNegocio("No se puede " + accion + ": " + admin.getNombre()
                    + " es el único administrador activo del sistema.");
        }
    }
}
