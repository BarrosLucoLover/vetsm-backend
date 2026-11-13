package cl.sanmarcos.vetsm.catalogo.service;

import cl.sanmarcos.vetsm.catalogo.dto.MovimientoStockRequest;
import cl.sanmarcos.vetsm.catalogo.dto.ProductoResponse;
import cl.sanmarcos.vetsm.catalogo.dto.ReservaResponse;
import cl.sanmarcos.vetsm.catalogo.model.Producto;
import cl.sanmarcos.vetsm.catalogo.model.TipoProducto;
import cl.sanmarcos.vetsm.catalogo.repository.ProductoRepository;
import cl.sanmarcos.vetsm.comun.exception.ApiException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stock de los artículos. ms-catalogo es el único dueño del stock: ms-ventas no lo modifica
 * directamente, se lo pide a este servicio.
 *
 * Una compra es una "saga" de dos pasos entre servicios:
 * 1. ms-ventas reserva el stock aquí (y recibe los precios actuales).
 * 2. ms-ventas cobra. Si el cobro falla, devuelve el stock reservado (acción compensatoria).
 */
@Service
@Transactional
public class InventarioService {

    /** Bajo este número de unidades, un artículo aparece como "stock bajo" en el panel. */
    static final int UMBRAL_STOCK_BAJO = 5;

    private final ProductoRepository productos;

    public InventarioService(ProductoRepository productos) {
        this.productos = productos;
    }

    /**
     * Valida todos los productos y descuenta el stock de los artículos. Es todo o nada: si un
     * producto no existe, no está a la venta o no tiene stock suficiente, no se descuenta ninguno.
     * Si dos compras piden la última unidad al mismo tiempo, el bloqueo optimista (@Version de
     * Producto) hace fallar a la segunda en vez de dejar el stock en negativo.
     */
    public ReservaResponse reservar(MovimientoStockRequest pedido) {
        List<ReservaResponse.Item> reservados = new ArrayList<>();
        agrupar(pedido).forEach((productoId, cantidad) -> {
            Producto producto = productos.findById(productoId)
                    .orElseThrow(() -> new ApiException.NoEncontrado("No existe el producto " + productoId + "."));
            if (!producto.isActivo()) {
                throw new ApiException.ReglaNegocio("\"" + producto.getNombre() + "\" ya no está disponible.");
            }
            if (!producto.hayStockPara(cantidad)) {
                throw new ApiException.Conflicto("Stock insuficiente de \"" + producto.getNombre() + "\": quedan "
                        + producto.getStock() + " y pediste " + cantidad + ".");
            }
            if (!producto.esServicio()) {
                producto.setStock(producto.getStock() - cantidad);
            }
            reservados.add(new ReservaResponse.Item(producto.getId(), producto.getNombre(), producto.getTipo(), producto.getPrecio(), cantidad));
        });
        return new ReservaResponse(reservados);
    }

    /** Devuelve unidades al stock (compra con pago rechazado o boleta anulada). Los servicios no tienen stock. */
    public void devolver(MovimientoStockRequest pedido) {
        agrupar(pedido).forEach((productoId, cantidad) -> productos.findById(productoId)
                .filter(p -> !p.esServicio())
                .ifPresent(p -> p.setStock(p.getStock() + cantidad)));
    }

    @Transactional(readOnly = true)
    public List<ProductoResponse> conStockBajo() {
        return productos.findByTipoAndActivoTrueOrderByNombreAsc(TipoProducto.PRODUCTO).stream()
                .filter(p -> p.getStock() != null && p.getStock() <= UMBRAL_STOCK_BAJO)
                .map(ProductoResponse::desde)
                .toList();
    }

    /** Si el mismo producto viene dos veces, se suman sus cantidades. */
    private static Map<Long, Integer> agrupar(MovimientoStockRequest pedido) {
        Map<Long, Integer> cantidades = new LinkedHashMap<>();
        pedido.items().forEach(i -> cantidades.merge(i.productoId(), i.cantidad(), Integer::sum));
        return cantidades;
    }
}
