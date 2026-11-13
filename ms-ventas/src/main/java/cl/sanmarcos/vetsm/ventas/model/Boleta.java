package cl.sanmarcos.vetsm.ventas.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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

    /** Id del cliente en ms-usuarios (otra base de datos, por eso no es una relación). */
    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    /** Nombre y correo del cliente al momento de comprar. Se copian del token: la boleta no cambia si después edita su perfil. */
    @Column(name = "cliente_nombre", nullable = false, length = 100)
    private String clienteNombre;

    @Column(name = "cliente_email", nullable = false, length = 150)
    private String clienteEmail;

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

    public Boleta(Long clienteId, String clienteNombre, String clienteEmail) {
        this.clienteId = clienteId;
        this.clienteNombre = clienteNombre;
        this.clienteEmail = clienteEmail;
    }

    /** Agrega una línea a la boleta con el nombre y el precio del momento (los entrega ms-catalogo), y recalcula el total. */
    public DetalleBoleta agregarDetalle(Long productoId, String nombreProducto, int precioUnitario, int cantidad) {
        DetalleBoleta detalle = new DetalleBoleta(this, productoId, nombreProducto, cantidad, precioUnitario);
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
