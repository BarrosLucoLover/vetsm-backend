package cl.sanmarcos.vetsm.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Ítem que se vende en la tienda y se puede agregar al carrito.
 * Puede ser un servicio de la clínica (sin stock, con duración) o un artículo físico (con stock).
 */
@Entity
@Table(name = "producto")
@Getter
@Setter
@NoArgsConstructor
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    /** Precio en pesos chilenos (CLP no usa decimales). */
    @Column(nullable = false)
    private Integer precio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoProducto tipo;

    /** Unidades disponibles. Es null en los servicios, que no manejan stock. */
    private Integer stock;

    /** Duración de la atención en minutos. Solo aplica a los servicios. */
    @Column(name = "duracion_min")
    private Integer duracionMin;

    @Column(name = "imagen_url", length = 300)
    private String imagenUrl;

    /** Un producto inactivo deja de mostrarse en la tienda, pero se conserva en las boletas antiguas. */
    @Column(nullable = false)
    private boolean activo = true;

    /**
     * Control de concurrencia optimista: si dos clientes compran el último artículo al mismo tiempo,
     * la segunda compra falla en vez de dejar el stock en negativo.
     */
    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false, foreignKey = @ForeignKey(name = "fk_producto_categoria"))
    private Categoria categoria;

    public static Producto servicio(String nombre, String descripcion, int precio, int duracionMin, Categoria categoria) {
        Producto p = new Producto();
        p.nombre = nombre;
        p.descripcion = descripcion;
        p.precio = precio;
        p.tipo = TipoProducto.SERVICIO;
        p.duracionMin = duracionMin;
        p.categoria = categoria;
        return p;
    }

    public static Producto articulo(String nombre, String descripcion, int precio, int stock, Categoria categoria) {
        Producto p = new Producto();
        p.nombre = nombre;
        p.descripcion = descripcion;
        p.precio = precio;
        p.tipo = TipoProducto.PRODUCTO;
        p.stock = stock;
        p.categoria = categoria;
        return p;
    }

    public boolean esServicio() {
        return tipo == TipoProducto.SERVICIO;
    }

    /** Los servicios siempre están disponibles; los artículos, solo si queda stock suficiente. */
    public boolean hayStockPara(int cantidad) {
        return esServicio() || (stock != null && stock >= cantidad);
    }
}
