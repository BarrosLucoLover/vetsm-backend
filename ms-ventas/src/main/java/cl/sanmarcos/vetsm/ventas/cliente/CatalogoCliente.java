package cl.sanmarcos.vetsm.ventas.cliente;

import cl.sanmarcos.vetsm.comun.cliente.ClienteInterno;
import cl.sanmarcos.vetsm.comun.security.TokenDeServicio;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cliente HTTP hacia ms-catalogo. ms-ventas no tiene la tabla de productos: le pide a ms-catalogo
 * que reserve el stock (y le entregue los precios actuales) o que lo devuelva.
 */
@Component
public class CatalogoCliente extends ClienteInterno {

    public record Item(Long productoId, Integer cantidad) {
    }

    record Pedido(List<Item> items) {
    }

    /** Producto reservado, con el nombre y el precio actual que tiene en ms-catalogo. */
    public record ItemReservado(Long productoId, String nombre, String tipo, Integer precio, Integer cantidad) {
    }

    public record Reserva(List<ItemReservado> items) {
    }

    public CatalogoCliente(@Value("${app.servicios.catalogo}") String url, TokenDeServicio tokens, JsonMapper json) {
        super("catálogo", url, tokens, json);
    }

    /** Descuenta el stock. Responde el error de ms-catalogo si falta stock (409) o un producto no está a la venta (422). */
    public Reserva reservar(List<Item> items) {
        return llamar(() -> rest.post().uri("/internal/v1/inventario/reservas").body(new Pedido(items)).retrieve().body(Reserva.class));
    }

    /** Acción compensatoria: devuelve al stock lo que se había reservado. */
    public void devolver(List<Item> items) {
        llamar(() -> rest.post().uri("/internal/v1/inventario/devoluciones").body(new Pedido(items)).retrieve().toBodilessEntity());
    }
}
