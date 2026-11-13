package cl.sanmarcos.vetsm.catalogo.service;

import cl.sanmarcos.vetsm.comun.util.Textos;
import cl.sanmarcos.vetsm.catalogo.dto.ProductoRequest;
import cl.sanmarcos.vetsm.catalogo.dto.ProductoResponse;
import cl.sanmarcos.vetsm.comun.exception.ApiException;
import cl.sanmarcos.vetsm.catalogo.model.Producto;
import cl.sanmarcos.vetsm.catalogo.model.TipoProducto;
import cl.sanmarcos.vetsm.catalogo.repository.ProductoRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductoService {

    private final ProductoRepository productos;
    private final CategoriaService categorias;

    public ProductoService(ProductoRepository productos, CategoriaService categorias) {
        this.productos = productos;
        this.categorias = categorias;
    }

    /** Catálogo. Por defecto solo muestra lo que está a la venta; el administrador puede pedir también lo dado de baja. */
    @Transactional(readOnly = true)
    public List<ProductoResponse> listar(Long categoriaId, TipoProducto tipo, boolean incluirInactivos) {
        List<Producto> base = incluirInactivos ? productos.findAllByOrderByNombreAsc() : productos.findByActivoTrueOrderByNombreAsc();
        return base.stream()
                .filter(p -> categoriaId == null || p.getCategoria().getId().equals(categoriaId))
                .filter(p -> tipo == null || p.getTipo() == tipo)
                .map(ProductoResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProductoResponse obtener(Long id) {
        return ProductoResponse.desde(buscar(id));
    }

    public ProductoResponse crear(ProductoRequest datos) {
        Producto producto = new Producto();
        aplicar(producto, datos);
        producto.setActivo(datos.activo() == null || datos.activo());
        return ProductoResponse.desde(productos.save(producto));
    }

    public ProductoResponse actualizar(Long id, ProductoRequest datos) {
        Producto producto = buscar(id);
        aplicar(producto, datos);
        if (datos.activo() != null) {
            producto.setActivo(datos.activo());
        }
        return ProductoResponse.desde(producto);
    }

    /**
     * Baja lógica: el producto deja de venderse, pero no se borra porque aparece en boletas y citas
     * anteriores (borrarlo rompería el historial de compras).
     */
    public void darDeBaja(Long id) {
        buscar(id).setActivo(false);
    }

    Producto buscar(Long id) {
        return productos.findById(id)
                .orElseThrow(() -> new ApiException.NoEncontrado("No existe el producto " + id + "."));
    }

    /** Copia los datos validando lo que depende del tipo: los artículos llevan stock y los servicios, duración. */
    private void aplicar(Producto producto, ProductoRequest datos) {
        Map<String, String> errores = new LinkedHashMap<>();
        if (datos.tipo() == TipoProducto.PRODUCTO && datos.stock() == null) {
            errores.put("stock", "Los productos deben indicar su stock.");
        }
        if (datos.tipo() == TipoProducto.SERVICIO && datos.duracionMin() == null) {
            errores.put("duracionMin", "Los servicios deben indicar su duración en minutos.");
        }
        if (!errores.isEmpty()) {
            throw new ApiException.DatosInvalidos(errores);
        }

        producto.setNombre(datos.nombre().trim());
        producto.setDescripcion(Textos.limpiar(datos.descripcion()));
        producto.setPrecio(datos.precio());
        producto.setTipo(datos.tipo());
        producto.setStock(datos.tipo() == TipoProducto.PRODUCTO ? datos.stock() : null);
        producto.setDuracionMin(datos.tipo() == TipoProducto.SERVICIO ? datos.duracionMin() : null);
        producto.setImagenUrl(Textos.limpiar(datos.imagenUrl()));
        producto.setCategoria(categorias.buscar(datos.categoriaId()));
    }
}
