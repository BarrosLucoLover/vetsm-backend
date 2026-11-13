package cl.sanmarcos.vetsm.agenda.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Paciente de la clínica. Cada mascota pertenece a un único dueño (usuario con rol CLIENTE).
 * El dueño vive en ms-usuarios (otra base de datos): aquí se guarda su id y una copia de su nombre
 * y teléfono, para que la recepción tenga a quién llamar sin depender de otro servicio.
 */
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

    @Column(name = "dueno_id", nullable = false)
    private Long duenoId;

    @Column(name = "dueno_nombre", nullable = false, length = 100)
    private String duenoNombre;

    @Column(name = "dueno_telefono", length = 20)
    private String duenoTelefono;

    public Mascota(String nombre, String especie, String raza, Long duenoId, String duenoNombre, String duenoTelefono) {
        this.nombre = nombre;
        this.especie = especie;
        this.raza = raza;
        this.duenoId = duenoId;
        this.duenoNombre = duenoNombre;
        this.duenoTelefono = duenoTelefono;
    }
}
