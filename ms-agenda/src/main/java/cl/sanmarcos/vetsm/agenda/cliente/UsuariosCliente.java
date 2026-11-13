package cl.sanmarcos.vetsm.agenda.cliente;

import cl.sanmarcos.vetsm.comun.cliente.ClienteInterno;
import cl.sanmarcos.vetsm.comun.security.TokenDeServicio;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Cliente HTTP hacia ms-usuarios: datos del dueño de una mascota (endpoint interno, rol SERVICIO). */
@Component
public class UsuariosCliente extends ClienteInterno {

    /** Solo los datos del usuario que necesita la agenda. */
    public record Usuario(Long id, String nombre, String telefono, String rol, boolean activo) {
    }

    public UsuariosCliente(@Value("${app.servicios.usuarios}") String url, TokenDeServicio tokens, JsonMapper json) {
        super("usuarios", url, tokens, json);
    }

    /** Responde el 404 de ms-usuarios si el usuario no existe. */
    public Usuario obtener(Long id) {
        return llamar(() -> rest.get().uri("/internal/v1/usuarios/{id}", id).retrieve().body(Usuario.class));
    }
}
