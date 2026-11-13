package cl.sanmarcos.vetsm.usuarios.service;

import cl.sanmarcos.vetsm.usuarios.dto.LoginRequest;
import cl.sanmarcos.vetsm.usuarios.dto.RegistroRequest;
import cl.sanmarcos.vetsm.usuarios.dto.SesionResponse;
import cl.sanmarcos.vetsm.usuarios.dto.UsuarioResponse;
import cl.sanmarcos.vetsm.comun.exception.ApiException;
import cl.sanmarcos.vetsm.usuarios.model.Usuario;
import cl.sanmarcos.vetsm.usuarios.repository.UsuarioRepository;
import cl.sanmarcos.vetsm.usuarios.security.TokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inicio de sesión, registro, renovación y cierre de sesión. */
@Service
@Transactional
public class AuthService {

    private static final String CREDENCIALES_INCORRECTAS = "Correo o contraseña incorrectos.";

    private final UsuarioRepository usuarios;
    private final UsuarioService usuarioService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;
    /** Hash de una contraseña cualquiera: se compara cuando el correo no existe, para tardar lo mismo. */
    private final String hashSenuelo;

    public AuthService(UsuarioRepository usuarios, UsuarioService usuarioService, PasswordEncoder passwordEncoder, TokenService tokens) {
        this.usuarios = usuarios;
        this.usuarioService = usuarioService;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.hashSenuelo = passwordEncoder.encode("contraseña-señuelo-para-igualar-tiempos");
    }

    /** Sesión iniciada: lo que va en el cuerpo de la respuesta y el token que va en la cookie. */
    public record Sesion(SesionResponse respuesta, TokenService.Tokens tokens) {
    }

    /**
     * Verifica correo y contraseña (contra el hash BCrypt). El mensaje de error es el mismo si el
     * correo no existe o si la contraseña es incorrecta, para no revelar qué correos están registrados.
     */
    public Sesion iniciarSesion(LoginRequest datos) {
        Usuario usuario = usuarios.findByEmail(datos.email().trim().toLowerCase()).orElse(null);
        String hash = usuario != null ? usuario.getPasswordHash() : hashSenuelo;
        boolean coincide = passwordEncoder.matches(datos.password(), hash);
        if (usuario == null || !coincide) {
            throw new ApiException.NoAutorizado(CREDENCIALES_INCORRECTAS);
        }
        if (!usuario.isActivo()) {
            throw new ApiException.NoAutorizado("Tu cuenta está desactivada. Comunícate con la clínica.");
        }
        return sesionPara(usuario, tokens.emitir(usuario));
    }

    /** Registro público: crea un CLIENTE y deja la sesión iniciada. */
    public Sesion registrar(RegistroRequest datos) {
        UsuarioResponse creado = usuarioService.registrarCliente(datos.nombre(), datos.email(), datos.password());
        Usuario usuario = usuarioService.buscar(creado.id());
        return sesionPara(usuario, tokens.emitir(usuario));
    }

    /** Cambia el token de renovación de la cookie por uno nuevo y entrega un token de acceso nuevo. */
    @Transactional(noRollbackFor = ApiException.NoAutorizado.class)
    public Sesion renovar(String tokenRenovacion) {
        TokenService.Renovacion renovacion = tokens.renovar(tokenRenovacion);
        return sesionPara(renovacion.usuario(), renovacion.tokens());
    }

    public void cerrarSesion(String tokenRenovacion) {
        tokens.revocar(tokenRenovacion);
    }

    /** Después de cambiar la contraseña: cierra todas las sesiones y abre una nueva para este dispositivo. */
    public TokenService.Tokens reiniciarSesiones(Long usuarioId) {
        tokens.revocarTodas(usuarioId);
        return tokens.emitir(usuarioService.buscar(usuarioId));
    }

    private Sesion sesionPara(Usuario usuario, TokenService.Tokens emitidos) {
        SesionResponse respuesta = new SesionResponse(emitidos.acceso(), "Bearer", emitidos.segundosAcceso(), UsuarioResponse.desde(usuario));
        return new Sesion(respuesta, emitidos);
    }
}
