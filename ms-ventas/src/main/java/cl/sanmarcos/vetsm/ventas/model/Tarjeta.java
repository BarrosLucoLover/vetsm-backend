package cl.sanmarcos.vetsm.ventas.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Tarjeta guardada en la cuenta de un cliente para pagar más rápido.
 *
 * Regla de seguridad: NUNCA se guarda el número completo ni el código de seguridad (CVV).
 * Solo la marca, los últimos 4 dígitos, el titular y el vencimiento. En un sistema real, la
 * pasarela de pago entregaría además un token para cobrar sin volver a pedir la tarjeta.
 */
@Entity
@Table(name = "tarjeta")
@Getter
@Setter
@NoArgsConstructor
public class Tarjeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Id del cliente en ms-usuarios. Es un número y no una relación (@ManyToOne) porque la tabla de
     * usuarios está en otra base de datos: cada microservicio es dueño de sus propias tablas.
     */
    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MarcaTarjeta marca;

    @Column(nullable = false, length = 4)
    private String ultimos4;

    @Column(nullable = false, length = 60)
    private String titular;

    @Column(name = "mes_vencimiento", nullable = false)
    private Integer mesVencimiento;

    @Column(name = "anio_vencimiento", nullable = false)
    private Integer anioVencimiento;

    @Column(nullable = false)
    private boolean predeterminada;

    public Tarjeta(Long usuarioId, MarcaTarjeta marca, String ultimos4, String titular, int mes, int anio) {
        this.usuarioId = usuarioId;
        this.marca = marca;
        this.ultimos4 = ultimos4;
        this.titular = titular;
        this.mesVencimiento = mes;
        this.anioVencimiento = anio;
    }

    /** La tarjeta sirve hasta el último día de su mes de vencimiento. */
    public boolean estaVencida(LocalDate hoy) {
        return YearMonth.of(anioVencimiento, mesVencimiento).isBefore(YearMonth.from(hoy));
    }
}
