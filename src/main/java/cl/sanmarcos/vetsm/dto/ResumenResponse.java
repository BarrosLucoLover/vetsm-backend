package cl.sanmarcos.vetsm.dto;

import java.util.List;
import java.util.Map;

/** Métricas del panel de administración. */
public record ResumenResponse(
        Map<String, Long> citasPorEstado,
        long citasDeHoy,
        List<Conteo> citasPorServicio,
        long boletasPagadas,
        long ventasTotales,
        List<VentaProducto> ventasPorProducto,
        long clientesActivos,
        List<ProductoResponse> productosConStockBajo) {

    public record Conteo(String etiqueta, long valor) {
    }

    public record VentaProducto(Long productoId, String nombre, long unidades, long monto) {
    }
}
