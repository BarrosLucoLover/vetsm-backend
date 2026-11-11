package cl.sanmarcos.vetsm.dto;

import cl.sanmarcos.vetsm.model.Producto;
import cl.sanmarcos.vetsm.model.TipoProducto;

/** Mismos nombres de campos que usa el frontend (src/data/catalogo.js). */
public record ProductoResponse(
        Long id,
        String nombre,
        String descripcion,
        Integer precio,
        TipoProducto tipo,
        Integer stock,
        Integer duracionMin,
        String imagenUrl,
        boolean activo,
        Long categoriaId,
        String categoriaNombre) {

    public static ProductoResponse desde(Producto p) {
        return new ProductoResponse(p.getId(), p.getNombre(), p.getDescripcion(), p.getPrecio(), p.getTipo(), p.getStock(),
                p.getDuracionMin(), p.getImagenUrl(), p.isActivo(), p.getCategoria().getId(), p.getCategoria().getNombre());
    }
}
