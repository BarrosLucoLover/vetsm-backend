package cl.sanmarcos.vetsm.agenda.repository;

import cl.sanmarcos.vetsm.agenda.model.Mascota;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MascotaRepository extends JpaRepository<Mascota, Long> {

    /** Mascotas de un dueño: es lo único que un cliente puede ver. */
    List<Mascota> findByDuenoIdOrderByNombreAsc(Long duenoId);

    List<Mascota> findAllByOrderByNombreAsc();

    long countByDuenoId(Long duenoId);
}
