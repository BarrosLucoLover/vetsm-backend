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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Hora de atención agendada. El servicio es un Producto de tipo SERVICIO.
 *
 * Puede venir de dos lugares:
 * - Un cliente registrado que elige una de sus mascotas: `mascota` apunta a ella.
 * - Una solicitud sin cuenta (formulario público de la portada o una persona que llega a recepción):
 *   `mascota` queda vacía y solo se guardan los datos de contacto.
 * En ambos casos se copian el nombre de la mascota, del dueño y el teléfono, para que la recepción
 * siempre tenga a quién llamar.
 */
@Entity
@Table(name = "cita")
@Getter
@Setter
@NoArgsConstructor
public class Cita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mascota_id", foreignKey = @ForeignKey(name = "fk_cita_mascota"))
    private Mascota mascota;

    @Column(name = "nombre_mascota", nullable = false, length = 60)
    private String nombreMascota;

    @Column(name = "nombre_dueno", nullable = false, length = 100)
    private String nombreDueno;

    @Column(name = "telefono_contacto", length = 20)
    private String telefonoContacto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servicio_id", nullable = false, foreignKey = @ForeignKey(name = "fk_cita_servicio"))
    private Producto servicio;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private LocalTime hora;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCita estado = EstadoCita.PENDIENTE;

    @Column(length = 500)
    private String notas;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    /** Cita de una mascota registrada: los datos de contacto se toman de la mascota y su dueño. */
    public Cita(Mascota mascota, Producto servicio, LocalDate fecha, LocalTime hora, EstadoCita estado, String notas) {
        asignarMascota(mascota);
        this.servicio = servicio;
        this.fecha = fecha;
        this.hora = hora;
        this.estado = estado;
        this.notas = notas;
    }

    public void asignarMascota(Mascota mascota) {
        this.mascota = mascota;
        this.nombreMascota = mascota.getNombre();
        this.nombreDueno = mascota.getDueno().getNombre();
        this.telefonoContacto = mascota.getDueno().getTelefono();
    }

    @PrePersist
    void antesDeCrear() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
    }
}
