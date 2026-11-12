package cl.sanmarcos.vetsm.service;

import cl.sanmarcos.vetsm.dto.CambioPasswordRequest;
import cl.sanmarcos.vetsm.dto.PerfilRequest;
import cl.sanmarcos.vetsm.dto.UsuarioRequest;
import cl.sanmarcos.vetsm.dto.UsuarioResponse;
import cl.sanmarcos.vetsm.dto.UsuarioUpdateRequest;
import cl.sanmarcos.vetsm.exception.ApiException;
import cl.sanmarcos.vetsm.model.Rol;
import cl.sanmarcos.vetsm.model.Usuario;
import cl.sanmarcos.vetsm.repository.BoletaRepository;
import cl.sanmarcos.vetsm.repository.MascotaRepository;
import cl.sanmarcos.vetsm.repository.RefreshTokenRepository;
import cl.sanmarcos.vetsm.repository.TarjetaRepository;
import cl.sanmarcos.vetsm.repository.UsuarioRepository;
import java.util.List;
import java.util.Map;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UsuarioService {

    private final UsuarioRepository usuarios;
    private final BoletaRepository boletas;
    private final MascotaRepository mascotas;
    private final TarjetaRepository tarjetas;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarios, BoletaRepository boletas, MascotaRepository mascotas,
            TarjetaRepository tarjetas, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.boletas = boletas;
        this.mascotas = mascotas;
        this.tarjetas = tarjetas;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
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
                passwordEncoder.encode(datos.password()), CategoriaService.limpiar(datos.telefono()), datos.rol());
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
        usuario.setTelefono(CategoriaService.limpiar(datos.telefono()));
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
     */
    public void eliminar(Long id, Long actorId) {
        Usuario usuario = buscar(id);
        if (id.equals(actorId)) {
            throw new ApiException.ReglaNegocio("No puedes eliminar tu propia cuenta.");
        }
        if (usuario.getRol() == Rol.ADMIN && usuario.isActivo()) {
            exigirOtroAdministradorActivo(usuario, "eliminarlo");
        }
        if (boletas.existsByClienteId(id) || mascotas.existsByDuenoId(id)) {
            throw new ApiException.Conflicto("La cuenta de " + usuario.getNombre()
                    + " tiene compras o mascotas registradas. Desactívala en lugar de eliminarla.");
        }
        tarjetas.deleteAll(tarjetas.findByUsuarioIdOrderByPredeterminadaDescIdDesc(id));
        refreshTokens.deleteAll(refreshTokens.findByUsuarioId(id));
        usuarios.delete(usuario);
    }

    /** "Mi cuenta": el usuario personaliza su perfil. No puede cambiar su correo ni su rol. */
    public UsuarioResponse actualizarPerfil(Long id, PerfilRequest datos) {
        Usuario usuario = buscar(id);
        usuario.setNombre(datos.nombre().trim());
        usuario.setTelefono(CategoriaService.limpiar(datos.telefono()));
        usuario.setRegion(CategoriaService.limpiar(datos.region()));
        usuario.setComuna(CategoriaService.limpiar(datos.comuna()));
        usuario.setDireccion(CategoriaService.limpiar(datos.direccion()));
        usuario.setAvatarIcono(CategoriaService.limpiar(datos.avatarIcono()));
        usuario.setAvatarColor(CategoriaService.limpiar(datos.avatarColor()));
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
