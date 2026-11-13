package cl.sanmarcos.vetsm.catalogo.repository;

import cl.sanmarcos.vetsm.catalogo.model.Producto;
import cl.sanmarcos.vetsm.catalogo.model.TipoProducto;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /** Catálogo visible en la tienda. */
    List<Producto> findByActivoTrueOrderByNombreAsc();

    List<Producto> findAllByOrderByNombreAsc();

    List<Producto> findByTipoAndActivoTrueOrderByNombreAsc(TipoProducto tipo);

    List<Producto> findByCategoriaIdAndActivoTrueOrderByNombreAsc(Long categoriaId);

    /** Sirve para impedir que se elimine una categoría que todavía tiene productos. */
    boolean existsByCategoriaId(Long categoriaId);
}
