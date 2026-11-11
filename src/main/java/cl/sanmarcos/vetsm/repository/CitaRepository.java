package cl.sanmarcos.vetsm.repository;

import cl.sanmarcos.vetsm.model.Cita;
import cl.sanmarcos.vetsm.model.EstadoCita;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CitaRepository extends JpaRepository<Cita, Long> {

    List<Cita> findAllByOrderByFechaAscHoraAsc();

    List<Cita> findByEstadoOrderByFechaAscHoraAsc(EstadoCita estado);

    List<Cita> findByFechaOrderByHoraAsc(LocalDate fecha);

    /** Citas de las mascotas de un dueño: es lo único que un cliente puede ver. */
    List<Cita> findByMascotaDuenoIdOrderByFechaDescHoraDesc(Long duenoId);

    /** ¿Hay otra cita vigente en ese horario? Las canceladas dejan el horario libre. */
    boolean existsByFechaAndHoraAndEstadoNot(LocalDate fecha, LocalTime hora, EstadoCita estado);

    /** Igual que la anterior, pero ignorando la propia cita (para cuando se reagenda). */
    boolean existsByFechaAndHoraAndEstadoNotAndIdNot(LocalDate fecha, LocalTime hora, EstadoCita estado, Long id);

    boolean existsByMascotaId(Long mascotaId);

    long countByEstado(EstadoCita estado);

    List<Cita> findByFechaAndEstadoOrderByHoraAsc(LocalDate fecha, EstadoCita estado);
}
