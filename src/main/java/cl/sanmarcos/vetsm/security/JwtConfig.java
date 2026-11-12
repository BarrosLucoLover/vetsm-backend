package cl.sanmarcos.vetsm.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Configuración de los tokens JWT.
 *
 * - Se firman con HMAC-SHA256 (HS256) usando una clave secreta que solo conoce el servidor.
 * - Al recibir un token, Spring Security verifica la firma, la fecha de expiración y el emisor.
 *   Si algo no calza (token alterado, vencido o de otro sistema) responde 401.
 * - El rol del usuario viaja en el claim "rol" y se convierte en la autoridad "ROLE_ADMIN",
 *   "ROLE_RECEPCIONISTA" o "ROLE_CLIENTE", que es lo que revisan las reglas @PreAuthorize.
 */
@Configuration
public class JwtConfig {

    private static final int LARGO_MINIMO_SECRETO = 32;

    @Bean
    public SecretKey claveJwt(@Value("${app.jwt.secreto}") String secreto) {
        byte[] bytes = secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < LARGO_MINIMO_SECRETO) {
            throw new IllegalStateException("app.jwt.secreto (variable JWT_SECRET) debe tener al menos "
                    + LARGO_MINIMO_SECRETO + " caracteres para firmar los tokens con HS256 de forma segura.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder jwtEncoder(SecretKey claveJwt) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(claveJwt));
    }

    @Bean
    public JwtDecoder jwtDecoder(SecretKey claveJwt, @Value("${app.jwt.emisor}") String emisor) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(claveJwt).macAlgorithm(MacAlgorithm.HS256).build();
        // Valida expiración (exp), "no antes de" (nbf) y que el emisor (iss) sea este backend.
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(emisor));
        return decoder;
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName(TokenService.CLAIM_ROL);
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(roles);
        return convertidor;
    }
}
