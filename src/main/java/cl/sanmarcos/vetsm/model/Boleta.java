package cl.sanmarcos.vetsm.model;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Compra realizada por un cliente al confirmar su carrito. Sus líneas están en DetalleBoleta. */
@Entity
@Table(name = "boleta")
@Getter
@Setter
@NoArgsConstructor
public class Boleta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false, foreignKey = @ForeignKey(name = "fk_boleta_cliente"))
    private Usuario cliente;

    @Column(nullable = false, updatable = false)
    private LocalDateTime fecha;

    /** Suma de los subtotales de los detalles, en pesos chilenos. */
    @Column(nullable = false)
    private Integer total = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoBoleta estado = EstadoBoleta.PAGADA;

    // ---------- Medio de pago: solo datos seguros, nunca el número completo ni el CVV ----------
    @Enumerated(EnumType.STRING)
    @Column(name = "pago_marca", length = 20)
    private MarcaTarjeta pagoMarca;

    @Column(name = "pago_ultimos4", length = 4)
    private String pagoUltimos4;

    @Column(name = "codigo_autorizacion", length = 12)
    private String codigoAutorizacion;

    @OneToMany(mappedBy = "boleta", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleBoleta> detalles = new ArrayList<>();

    public Boleta(Usuario cliente) {
        this.cliente = cliente;
    }

    /** Agrega una línea a la boleta, guardando el precio del momento, y recalcula el total. */
    public DetalleBoleta agregarDetalle(Producto producto, int cantidad) {
        DetalleBoleta detalle = new DetalleBoleta(this, producto, cantidad, producto.getPrecio());
        detalles.add(detalle);
        recalcularTotal();
        return detalle;
    }

    public void recalcularTotal() {
        total = detalles.stream().mapToInt(DetalleBoleta::getSubtotal).sum();
    }

    @PrePersist
    void antesDeCrear() {
        if (fecha == null) {
            fecha = LocalDateTime.now();
        }
    }
}
