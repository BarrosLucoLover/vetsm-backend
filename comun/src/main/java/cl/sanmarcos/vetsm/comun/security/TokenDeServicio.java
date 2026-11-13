package cl.sanmarcos.vetsm.comun.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

/**
 * Token con el que un microservicio se identifica al llamar a otro (por ejemplo, ms-ventas pidiéndole
 * a ms-catalogo que reserve stock). Es un JWT normal, firmado con el mismo secreto, pero con el rol
 * SERVICIO: los endpoints internos (/internal/v1/...) solo aceptan ese rol, así que ningún usuario,
 * ni siquiera un administrador, puede llamarlos. Dura un minuto: se crea uno nuevo en cada llamada.
 */
@Component
public class TokenDeServicio {

    private static final Duration DURACION = Duration.ofMinutes(1);

    private final JwtEncoder encoder;
    private final Clock reloj;
    private final String emisor;
    private final String nombreServicio;

    public TokenDeServicio(JwtEncoder encoder, Clock reloj, @Value("${app.jwt.emisor}") String emisor,
            @Value("${spring.application.name}") String nombreServicio) {
        this.encoder = encoder;
        this.reloj = reloj;
        this.emisor = emisor;
        this.nombreServicio = nombreServicio;
    }

    public String emitir() {
        Instant ahora = Instant.now(reloj);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(emisor)
                .issuedAt(ahora)
                .expiresAt(ahora.plus(DURACION))
                .subject("servicio:" + nombreServicio)
                .claim(JwtConfig.CLAIM_ROL, SeguridadBase.ROL_SERVICIO)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }
}
