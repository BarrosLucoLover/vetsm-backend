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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tarjeta_usuario"))
    private Usuario usuario;

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

    public Tarjeta(Usuario usuario, MarcaTarjeta marca, String ultimos4, String titular, int mes, int anio) {
        this.usuario = usuario;
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
