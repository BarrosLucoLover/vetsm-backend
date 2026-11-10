package cl.sanmarcos.vetsm.model;

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

/** Paciente de la clínica. Cada mascota pertenece a un único dueño (usuario con rol CLIENTE). */
@Entity
@Table(name = "mascota")
@Getter
@Setter
@NoArgsConstructor
public class Mascota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String nombre;

    /** Perro, gato, ave, etc. */
    @Column(nullable = false, length = 40)
    private String especie;

    @Column(length = 60)
    private String raza;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dueno_id", nullable = false, foreignKey = @ForeignKey(name = "fk_mascota_dueno"))
    private Usuario dueno;

    public Mascota(String nombre, String especie, String raza, Usuario dueno) {
        this.nombre = nombre;
        this.especie = especie;
        this.raza = raza;
        this.dueno = dueno;
    }
}
