package cl.sanmarcos.vetsm.ventas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Línea de una boleta: un producto, su cantidad y el precio al que se vendió.
 * El precio se copia aquí para que la boleta no cambie si después se modifica el precio del producto.
 */
@Entity
@Table(name = "detalle_boleta")
@Getter
@Setter
@NoArgsConstructor
public class DetalleBoleta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "boleta_id", nullable = false, foreignKey = @ForeignKey(name = "fk_detalle_boleta"))
    private Boleta boleta;

    /** Id del producto en ms-catalogo (otra base de datos, por eso no es una relación). */
    @Column(name = "producto_id", nullable = false)
    private Long productoId;

    /** Nombre del producto al momento de la venta: la boleta sigue igual aunque después lo renombren. */
    @Column(name = "nombre_producto", nullable = false, length = 120)
    private String nombreProducto;

    @Column(nullable = false)
    private Integer cantidad;

    @Column(name = "precio_unitario", nullable = false)
    private Integer precioUnitario;

    @Column(nullable = false)
    private Integer subtotal;

    public DetalleBoleta(Boleta boleta, Long productoId, String nombreProducto, int cantidad, int precioUnitario) {
        this.boleta = boleta;
        this.productoId = productoId;
        this.nombreProducto = nombreProducto;
        this.cantidad = cantidad;
        this.precioUnitario = precioUnitario;
        this.subtotal = cantidad * precioUnitario;
    }
}
