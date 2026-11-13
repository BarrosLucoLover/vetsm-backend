package cl.sanmarcos.vetsm.catalogo.service;

import cl.sanmarcos.vetsm.comun.util.Textos;
import cl.sanmarcos.vetsm.catalogo.dto.CategoriaRequest;
import cl.sanmarcos.vetsm.catalogo.dto.CategoriaResponse;
import cl.sanmarcos.vetsm.comun.exception.ApiException;
import cl.sanmarcos.vetsm.catalogo.model.Categoria;
import cl.sanmarcos.vetsm.catalogo.repository.CategoriaRepository;
import cl.sanmarcos.vetsm.catalogo.repository.ProductoRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CategoriaService {

    private final CategoriaRepository categorias;
    private final ProductoRepository productos;

    public CategoriaService(CategoriaRepository categorias, ProductoRepository productos) {
        this.categorias = categorias;
        this.productos = productos;
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listar() {
        return categorias.findAll().stream()
                .sorted(Comparator.comparing(Categoria::getId))
                .map(CategoriaResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoriaResponse obtener(Long id) {
        return CategoriaResponse.desde(buscar(id));
    }

    public CategoriaResponse crear(CategoriaRequest datos) {
        String nombre = datos.nombre().trim();
        if (categorias.existsByNombreIgnoreCase(nombre)) {
            throw new ApiException.Conflicto("Ya existe una categoría llamada \"" + nombre + "\".");
        }
        return CategoriaResponse.desde(categorias.save(new Categoria(nombre, Textos.limpiar(datos.descripcion()))));
    }

    public CategoriaResponse actualizar(Long id, CategoriaRequest datos) {
        Categoria categoria = buscar(id);
        String nombre = datos.nombre().trim();
        categorias.findByNombreIgnoreCase(nombre)
                .filter(otra -> !otra.getId().equals(id))
                .ifPresent(otra -> {
                    throw new ApiException.Conflicto("Ya existe una categoría llamada \"" + nombre + "\".");
                });
        categoria.setNombre(nombre);
        categoria.setDescripcion(Textos.limpiar(datos.descripcion()));
        return CategoriaResponse.desde(categoria);
    }

    /** Solo se puede eliminar una categoría vacía: así ningún producto queda sin categoría. */
    public void eliminar(Long id) {
        Categoria categoria = buscar(id);
        if (productos.existsByCategoriaId(id)) {
            throw new ApiException.Conflicto("La categoría \"" + categoria.getNombre()
                    + "\" tiene productos. Muévelos a otra categoría antes de eliminarla.");
        }
        categorias.delete(categoria);
    }

    Categoria buscar(Long id) {
        return categorias.findById(id)
                .orElseThrow(() -> new ApiException.NoEncontrado("No existe la categoría " + id + "."));
    }

}
