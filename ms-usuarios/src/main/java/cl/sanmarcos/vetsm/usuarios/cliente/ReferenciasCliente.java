package cl.sanmarcos.vetsm.usuarios.cliente;

import cl.sanmarcos.vetsm.comun.cliente.ClienteInterno;
import cl.sanmarcos.vetsm.comun.security.TokenDeServicio;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Pregunta a ms-ventas y ms-agenda si un usuario tiene datos guardados allá (compras o mascotas),
 * antes de eliminar su cuenta. Cada servicio es dueño de sus datos: ms-usuarios no puede mirar
 * directamente las tablas de los otros, tiene que preguntarles por su API.
 */
@Component
public class ReferenciasCliente {

    /** Respuesta de los endpoints internos de referencias: cuántos registros tiene el usuario. */
    public record Referencias(long cantidad, String detalle) {
    }

    private final Ventas ventas;
    private final Agenda agenda;

    public ReferenciasCliente(@Value("${app.servicios.ventas}") String urlVentas, @Value("${app.servicios.agenda}") String urlAgenda,
            TokenDeServicio tokens, JsonMapper json) {
        this.ventas = new Ventas(urlVentas, tokens, json);
        this.agenda = new Agenda(urlAgenda, tokens, json);
    }

    public Referencias enVentas(Long usuarioId) {
        return ventas.referencias(usuarioId);
    }

    public Referencias enAgenda(Long usuarioId) {
        return agenda.referencias(usuarioId);
    }

    /** Borra las tarjetas guardadas del usuario en ms-ventas (al eliminar la cuenta). */
    public void eliminarTarjetas(Long usuarioId) {
        ventas.eliminarTarjetas(usuarioId);
    }

    private static final class Ventas extends ClienteInterno {
        Ventas(String url, TokenDeServicio tokens, JsonMapper json) {
            super("ventas", url, tokens, json);
        }

        Referencias referencias(Long id) {
            return llamar(() -> rest.get().uri("/internal/v1/clientes/{id}/referencias", id).retrieve().body(Referencias.class));
        }

        void eliminarTarjetas(Long id) {
            llamar(() -> rest.delete().uri("/internal/v1/clientes/{id}/tarjetas", id).retrieve().toBodilessEntity());
        }
    }

    private static final class Agenda extends ClienteInterno {
        Agenda(String url, TokenDeServicio tokens, JsonMapper json) {
            super("agenda", url, tokens, json);
        }

        Referencias referencias(Long id) {
            return llamar(() -> rest.get().uri("/internal/v1/clientes/{id}/referencias", id).retrieve().body(Referencias.class));
        }
    }
}
