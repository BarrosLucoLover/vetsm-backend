package cl.sanmarcos.vetsm.repository;

import cl.sanmarcos.vetsm.model.Boleta;
import cl.sanmarcos.vetsm.model.EstadoBoleta;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BoletaRepository extends JpaRepository<Boleta, Long> {

    List<Boleta> findAllByOrderByFechaDesc();

    /** Compras de un cliente: es lo único que un cliente puede ver. */
    List<Boleta> findByClienteIdOrderByFechaDesc(Long clienteId);

    boolean existsByClienteId(Long clienteId);

    long countByEstado(EstadoBoleta estado);

    @Query("select coalesce(sum(b.total), 0) from Boleta b where b.estado = :estado")
    long sumarTotalPorEstado(@Param("estado") EstadoBoleta estado);

    /** Unidades y monto vendidos por producto, del más vendido al menos vendido. */
    @Query("""
            select d.producto.id as productoId, d.producto.nombre as nombre,
                   sum(d.cantidad) as unidades, sum(d.subtotal) as monto
            from DetalleBoleta d
            where d.boleta.estado = :estado
            group by d.producto.id, d.producto.nombre
            order by sum(d.subtotal) desc
            """)
    List<VentaPorProducto> ventasPorProducto(@Param("estado") EstadoBoleta estado);

    /** Proyección de la consulta de ventas por producto. */
    interface VentaPorProducto {
        Long getProductoId();

        String getNombre();

        Long getUnidades();

        Long getMonto();
    }
}
