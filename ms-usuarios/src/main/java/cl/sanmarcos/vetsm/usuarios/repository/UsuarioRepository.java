package cl.sanmarcos.vetsm.usuarios.repository;

import cl.sanmarcos.vetsm.comun.security.Rol;
import cl.sanmarcos.vetsm.usuarios.model.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    /** El correo se guarda en minúsculas; quien llama debe pasarlo ya normalizado. */
    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Usuario> findByRol(Rol rol);

    List<Usuario> findAllByOrderByNombreAsc();

    List<Usuario> findByRolOrderByNombreAsc(Rol rol);

    long countByRolAndActivoTrue(Rol rol);
}
