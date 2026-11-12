package cl.sanmarcos.vetsm.security;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Cookie que guarda el token de renovación en el navegador.
 * - HttpOnly: JavaScript no la puede leer, así que un script malicioso (XSS) no puede robarla.
 * - SameSite=Strict: el navegador no la envía desde otros sitios (protege contra CSRF).
 * - Path=/api/v1/auth: solo viaja a los endpoints de sesión, no en cada petición.
 * - Secure: solo por HTTPS. Se activa con app.jwt.cookie-segura=true cuando el sitio tenga HTTPS.
 */
@Component
public class CookieSesion {

    public static final String NOMBRE = "vetsm_refresh";
    private static final String RUTA = "/api/v1/auth";

    private final boolean segura;

    public CookieSesion(@Value("${app.jwt.cookie-segura:false}") boolean segura) {
        this.segura = segura;
    }

    public ResponseCookie crear(String token, Duration duracion) {
        return base(token).maxAge(duracion).build();
    }

    /** Cookie vacía que vence de inmediato: el navegador la borra. */
    public ResponseCookie borrar() {
        return base("").maxAge(0).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String valor) {
        return ResponseCookie.from(NOMBRE, valor).httpOnly(true).secure(segura).sameSite("Strict").path(RUTA);
    }
}
