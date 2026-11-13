package cl.sanmarcos.vetsm.catalogo.dto;

import cl.sanmarcos.vetsm.catalogo.model.TipoProducto;
import java.util.List;

/**
 * Resultado de una reserva de stock: cada producto con el nombre y el PRECIO ACTUAL de la base de
 * datos. ms-ventas usa estos datos para la boleta, así el precio nunca viene del navegador.
 */
public record ReservaResponse(List<Item> items) {

    public record Item(Long productoId, String nombre, TipoProducto tipo, Integer precio, Integer cantidad) {
    }
}
