package cl.sanmarcos.vetsm.agenda.cliente;

import cl.sanmarcos.vetsm.comun.cliente.ClienteInterno;
import cl.sanmarcos.vetsm.comun.security.TokenDeServicio;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Cliente HTTP hacia ms-catalogo: los servicios de la clínica (consulta, vacunación...) son productos del catálogo. */
@Component
public class CatalogoCliente extends ClienteInterno {

    /** Solo los datos del producto que necesita la agenda. */
    public record Producto(Long id, String nombre, String tipo, boolean activo) {
    }

    public CatalogoCliente(@Value("${app.servicios.catalogo}") String url, TokenDeServicio tokens, JsonMapper json) {
        super("catálogo", url, tokens, json);
    }

    /** Responde el 404 de ms-catalogo si el producto no existe. */
    public Producto obtener(Long id) {
        return llamar(() -> rest.get().uri("/api/v1/productos/{id}", id).retrieve().body(Producto.class));
    }

    /** Servicios de la clínica que están a la venta (para el reporte de citas por servicio). */
    public List<Producto> serviciosActivos() {
        return llamar(() -> rest.get().uri("/api/v1/productos?tipo=SERVICIO").retrieve()
                .body(new ParameterizedTypeReference<List<Producto>>() { }));
    }
}
