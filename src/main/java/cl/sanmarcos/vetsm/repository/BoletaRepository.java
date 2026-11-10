package cl.sanmarcos.vetsm.repository;

import cl.sanmarcos.vetsm.model.Boleta;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoletaRepository extends JpaRepository<Boleta, Long> {

    List<Boleta> findAllByOrderByFechaDesc();

    /** Compras de un cliente: es lo único que un cliente puede ver. */
    List<Boleta> findByClienteIdOrderByFechaDesc(Long clienteId);
}
