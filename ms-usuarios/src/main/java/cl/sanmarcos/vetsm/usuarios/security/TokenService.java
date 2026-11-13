package cl.sanmarcos.vetsm.usuarios.security;

import cl.sanmarcos.vetsm.comun.security.JwtConfig;
import cl.sanmarcos.vetsm.comun.exception.ApiException;
import cl.sanmarcos.vetsm.usuarios.model.RefreshToken;
import cl.sanmarcos.vetsm.usuarios.model.Usuario;
import cl.sanmarcos.vetsm.usuarios.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emite y renueva los tokens de una sesión.
 *
 * - Token de ACCESO (JWT, dura pocos minutos): se envía en cada petición en la cabecera
 *   "Authorization: Bearer ...". Lleva el id, el rol, el nombre y el correo del usuario.
 * - Token de RENOVACIÓN (aleatorio, dura días): viaja en una cookie HttpOnly que JavaScript no
 *   puede leer. Sirve una sola vez: cada renovación lo cambia por uno nuevo (rotación).
 *   Si un token ya usado aparece otra vez, se asume que fue robado y se cierran todas las sesiones.
 *   Excepción: si reaparece pocos segundos después de cambiarse, lo más probable es que dos pestañas
 *   del mismo navegador hayan renovado a la vez. Ese intento recibe 401 pero no cierra las sesiones.
 */
@Service
@Transactional
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final String SESION_INVALIDA = "Tu sesión expiró o ya no es válida. Inicia sesión nuevamente.";

    private final JwtEncoder encoder;
    private final RefreshTokenRepository refreshTokens;
    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();
    private final String emisor;
    private final Duration duracionAcceso;
    private final Duration duracionRenovacion;
    private final Duration gracia;

    public TokenService(JwtEncoder encoder, RefreshTokenRepository refreshTokens, Clock reloj,
            @Value("${app.jwt.emisor}") String emisor,
            @Value("${app.jwt.minutos-acceso:15}") long minutosAcceso,
            @Value("${app.jwt.dias-renovacion:7}") long diasRenovacion,
            @Value("${app.jwt.segundos-gracia:10}") long segundosGracia) {
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
        this.reloj = reloj;
        this.emisor = emisor;
        this.duracionAcceso = Duration.ofMinutes(minutosAcceso);
        this.duracionRenovacion = Duration.ofDays(diasRenovacion);
        this.gracia = Duration.ofSeconds(segundosGracia);
    }

    /** Tokens recién emitidos. El de renovación va en texto plano solo para ponerlo en la cookie. */
    public record Tokens(String acceso, long segundosAcceso, String renovacion, Duration duracionRenovacion) {
    }

    /** Inicia una sesión nueva para el usuario. */
    public Tokens emitir(Usuario usuario) {
        return new Tokens(crearAcceso(usuario), duracionAcceso.toSeconds(), crearRenovacion(usuario), duracionRenovacion);
    }

    /**
     * Cambia un token de renovación por tokens nuevos. Devuelve también el usuario dueño de la sesión.
     * Responde 401 si el token no existe, está vencido, fue revocado o la cuenta está desactivada.
     * noRollbackFor: aunque responda 401, la revocación de sesiones (por ejemplo, ante un robo) sí se guarda.
     */
    @Transactional(noRollbackFor = ApiException.NoAutorizado.class)
    public Renovacion renovar(String tokenPlano) {
        if (tokenPlano == null || tokenPlano.isBlank()) {
            throw new ApiException.NoAutorizado(SESION_INVALIDA);
        }
        RefreshToken guardado = refreshTokens.findByTokenHash(hash(tokenPlano))
                .orElseThrow(() -> new ApiException.NoAutorizado(SESION_INVALIDA));
        Usuario usuario = guardado.getUsuario();
        Instant ahora = Instant.now(reloj);

        if (guardado.isRevocado()) {
            if (guardado.isReemplazado() && !esRenovacionSimultanea(guardado, ahora)) {
                // Un token que ya se cambió por otro volvió a aparecer: alguien más tiene una copia.
                log.warn("Se reutilizó un token de renovación ya usado del usuario {}. Se cierran todas sus sesiones.", usuario.getId());
                revocarTodas(usuario.getId());
            }
            throw new ApiException.NoAutorizado(SESION_INVALIDA);
        }
        if (guardado.estaVencido(ahora) || !usuario.isActivo()) {
            guardado.setRevocado(true);
            throw new ApiException.NoAutorizado(SESION_INVALIDA);
        }
        // Rotación: este token queda usado y se entrega uno nuevo.
        guardado.setRevocado(true);
        guardado.setReemplazado(true);
        guardado.setReemplazadoEn(ahora);
        return new Renovacion(usuario, emitir(usuario));
    }

    /** Dos pestañas que renuevan al mismo tiempo usan la misma cookie con segundos de diferencia. */
    private boolean esRenovacionSimultanea(RefreshToken token, Instant ahora) {
        return token.getReemplazadoEn() != null && ahora.isBefore(token.getReemplazadoEn().plus(gracia));
    }

    public record Renovacion(Usuario usuario, Tokens tokens) {
    }

    /** Cierra la sesión del token indicado (si existe). */
    public void revocar(String tokenPlano) {
        if (tokenPlano != null && !tokenPlano.isBlank()) {
            refreshTokens.findByTokenHash(hash(tokenPlano)).ifPresent(t -> t.setRevocado(true));
        }
    }

    /** Cierra todas las sesiones del usuario (por ejemplo, después de cambiar la contraseña). */
    public void revocarTodas(Long usuarioId) {
        refreshTokens.findByUsuarioIdAndRevocadoFalse(usuarioId).forEach(t -> t.setRevocado(true));
    }

    private String crearAcceso(Usuario usuario) {
        Instant ahora = Instant.now(reloj);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(emisor)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(duracionAcceso))
                .subject(String.valueOf(usuario.getId()))
                .claim(JwtConfig.CLAIM_ROL, usuario.getRol().name())
                .claim(JwtConfig.CLAIM_EMAIL, usuario.getEmail())
                .claim(JwtConfig.CLAIM_NOMBRE, usuario.getNombre())
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
    }

    private String crearRenovacion(Usuario usuario) {
        byte[] bytes = new byte[32];
        azar.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant ahora = Instant.now(reloj);
        refreshTokens.save(new RefreshToken(usuario, hash(token), ahora, ahora.plus(duracionRenovacion)));
        return token;
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }
}
