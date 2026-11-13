package cl.sanmarcos.vetsm.catalogo.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.sanmarcos.vetsm.catalogo.config.DataSeeder;
import cl.sanmarcos.vetsm.catalogo.dto.MovimientoStockRequest;
import cl.sanmarcos.vetsm.catalogo.dto.ProductoRequest;
import cl.sanmarcos.vetsm.catalogo.model.Producto;
import cl.sanmarcos.vetsm.catalogo.model.TipoProducto;
import cl.sanmarcos.vetsm.catalogo.repository.CategoriaRepository;
import cl.sanmarcos.vetsm.catalogo.repository.ProductoRepository;
import cl.sanmarcos.vetsm.comun.exception.ApiException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Reglas del catálogo y del inventario, contra la base H2 de ms-catalogo con el catálogo inicial. */
@SpringBootTest
@Transactional
class ReglasCatalogoTest {

    private static final long CONSULTA = 1L;
    private static final long COLLAR = 9L;

    @Autowired private ProductoService productos;
    @Autowired private CategoriaService categorias;
    @Autowired private InventarioService inventario;
    @Autowired private ProductoRepository productoRepo;
    @Autowired private CategoriaRepository categoriaRepo;
    @Autowired private DataSeeder seeder;

    private static MovimientoStockRequest pedido(long productoId, int cantidad) {
        return new MovimientoStockRequest(List.of(new MovimientoStockRequest.Item(productoId, cantidad)));
    }

    private int stock(long id) {
        return productoRepo.findById(id).orElseThrow().getStock();
    }

    @Test
    @DisplayName("El catálogo inicial tiene 4 categorías, 6 servicios sin stock y 6 artículos con stock, sin duplicarse")
    void catalogoInicial() {
        assertThat(categoriaRepo.count()).isEqualTo(4);
        assertThat(productoRepo.findAll().stream().filter(Producto::esServicio)).hasSize(6).allMatch(p -> p.getStock() == null);
        assertThat(productoRepo.findAll().stream().filter(p -> !p.esServicio())).hasSize(6).allMatch(p -> p.getStock() > 0);
        seeder.run();
        assertThat(productoRepo.count()).isEqualTo(12);
    }

    @Test
    @DisplayName("Un artículo exige stock y un servicio exige duración")
    void segunTipo() {
        ProductoRequest articuloSinStock = new ProductoRequest("Correa", null, 9990, TipoProducto.PRODUCTO, null, null, null, 3L, null);
        assertThatThrownBy(() -> productos.crear(articuloSinStock))
                .isInstanceOf(ApiException.DatosInvalidos.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrores()).containsKey("stock"));

        var creado = productos.crear(new ProductoRequest("Peluquería", null, 15000, TipoProducto.SERVICIO, 50, 40, null, 1L, null));
        assertThat(creado.stock()).isNull();
        assertThat(creado.duracionMin()).isEqualTo(40);
        assertThat(creado.activo()).isTrue();
    }

    @Test
    @DisplayName("La baja de un producto lo saca del catálogo público pero no lo borra")
    void bajaLogica() {
        productos.darDeBaja(COLLAR);
        assertThat(productos.listar(null, null, false)).noneMatch(p -> p.id().equals(COLLAR));
        assertThat(productos.listar(null, null, true)).anyMatch(p -> p.id().equals(COLLAR) && !p.activo());
    }

    @Test
    @DisplayName("Una categoría con productos no se elimina")
    void categoriaConProductos() {
        assertThatThrownBy(() -> categorias.eliminar(1L)).isInstanceOf(ApiException.Conflicto.class);
    }

    @Test
    @DisplayName("Reservar descuenta el stock de los artículos y entrega el precio de la base de datos")
    void reservar() {
        var reserva = inventario.reservar(new MovimientoStockRequest(List.of(
                new MovimientoStockRequest.Item(COLLAR, 2),
                new MovimientoStockRequest.Item(CONSULTA, 1),
                new MovimientoStockRequest.Item(COLLAR, 1))));
        // El mismo producto dos veces se suma en una sola línea.
        assertThat(reserva.items()).hasSize(2);
        assertThat(reserva.items().get(0).cantidad()).isEqualTo(3);
        assertThat(reserva.items().get(0).precio()).isEqualTo(12990);
        assertThat(stock(COLLAR)).isEqualTo(27);
        // Los servicios no manejan stock.
        assertThat(productoRepo.findById(CONSULTA).orElseThrow().getStock()).isNull();
    }

    @Test
    @DisplayName("No se reserva más que el stock ni productos dados de baja")
    void reservarConReglas() {
        assertThatThrownBy(() -> inventario.reservar(pedido(COLLAR, 31)))
                .isInstanceOf(ApiException.Conflicto.class)
                .hasMessageContaining("quedan 30");
        productos.darDeBaja(COLLAR);
        assertThatThrownBy(() -> inventario.reservar(pedido(COLLAR, 1))).isInstanceOf(ApiException.ReglaNegocio.class);
        assertThatThrownBy(() -> inventario.reservar(pedido(999L, 1))).isInstanceOf(ApiException.NoEncontrado.class);
    }

    @Test
    @DisplayName("Devolver (compensación) repone el stock reservado")
    void devolver() {
        inventario.reservar(pedido(COLLAR, 5));
        inventario.devolver(pedido(COLLAR, 5));
        assertThat(stock(COLLAR)).isEqualTo(30);
    }

    @Test
    @DisplayName("El reporte de inventario muestra los artículos con 5 unidades o menos")
    void stockBajo() {
        assertThat(inventario.conStockBajo()).isEmpty();
        inventario.reservar(pedido(COLLAR, 26));
        assertThat(inventario.conStockBajo()).extracting(p -> p.nombre()).containsExactly("Collar antipulgas");
    }
}
