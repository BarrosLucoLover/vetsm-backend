package cl.sanmarcos.vetsm.service;

import cl.sanmarcos.vetsm.dto.CategoriaRequest;
import cl.sanmarcos.vetsm.dto.CategoriaResponse;
import cl.sanmarcos.vetsm.exception.ApiException;
import cl.sanmarcos.vetsm.model.Categoria;
import cl.sanmarcos.vetsm.repository.CategoriaRepository;
import cl.sanmarcos.vetsm.repository.ProductoRepository;
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
        return CategoriaResponse.desde(categorias.save(new Categoria(nombre, limpiar(datos.descripcion()))));
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
        categoria.setDescripcion(limpiar(datos.descripcion()));
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

    static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
