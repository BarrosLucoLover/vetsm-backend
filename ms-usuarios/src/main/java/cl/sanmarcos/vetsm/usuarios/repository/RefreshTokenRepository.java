package cl.sanmarcos.vetsm.usuarios.repository;

import cl.sanmarcos.vetsm.usuarios.model.RefreshToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUsuarioIdAndRevocadoFalse(Long usuarioId);

    List<RefreshToken> findByUsuarioId(Long usuarioId);
}
