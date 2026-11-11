package cl.sanmarcos.vetsm.repository;

import cl.sanmarcos.vetsm.model.Tarjeta;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TarjetaRepository extends JpaRepository<Tarjeta, Long> {

    /** Tarjetas de un cliente, con la predeterminada primero y luego las más recientes. */
    List<Tarjeta> findByUsuarioIdOrderByPredeterminadaDescIdDesc(Long usuarioId);

    /** Busca una tarjeta asegurando que sea del cliente: así nadie puede usar o borrar tarjetas ajenas. */
    Optional<Tarjeta> findByIdAndUsuarioId(Long id, Long usuarioId);

    long countByUsuarioId(Long usuarioId);
}
