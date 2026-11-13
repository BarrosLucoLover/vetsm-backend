package cl.sanmarcos.vetsm.usuarios.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.sanmarcos.vetsm.comun.security.Rol;
import cl.sanmarcos.vetsm.usuarios.config.DataSeeder;
import cl.sanmarcos.vetsm.usuarios.model.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** Repositorio de usuarios y carga inicial de las cuentas, sobre la base H2 de ms-usuarios. */
@SpringBootTest
@Transactional
class RepositoriosUsuariosTest {

    @Autowired private UsuarioRepository usuarios;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DataSeeder seeder;

    @Value("${app.seed.admin-password}")
    private String adminPassword;

    @Test
    @DisplayName("Se crean las cuentas iniciales con ids fijos y la contraseña guardada como hash BCrypt")
    void cuentasIniciales() {
        Usuario admin = usuarios.findByEmail("admin@sanmarcos.cl").orElseThrow();
        assertThat(admin.getId()).isEqualTo(1L);
        assertThat(admin.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(admin.getPasswordHash()).isNotEqualTo(adminPassword).startsWith("$2");
        assertThat(passwordEncoder.matches(adminPassword, admin.getPasswordHash())).isTrue();
        // ms-agenda usa el id 3 para las mascotas de demostración de la clienta Ana.
        assertThat(usuarios.findByEmail("ana.garcia@correo.cl")).get().extracting(Usuario::getId).isEqualTo(3L);
    }

    @Test
    @DisplayName("Volver a ejecutar la carga inicial no duplica cuentas")
    void noDuplica() {
        long antes = usuarios.count();
        seeder.run();
        assertThat(usuarios.count()).isEqualTo(antes);
    }

    @Test
    @DisplayName("Busca por correo y filtra por rol")
    void busquedas() {
        assertThat(usuarios.existsByEmail("ana.garcia@correo.cl")).isTrue();
        assertThat(usuarios.existsByEmail("nadie@correo.cl")).isFalse();
        assertThat(usuarios.findByRol(Rol.CLIENTE)).hasSize(1);
        assertThat(usuarios.countByRolAndActivoTrue(Rol.ADMIN)).isEqualTo(1);
    }

    @Test
    @DisplayName("La base de datos rechaza dos usuarios con el mismo correo")
    void correoUnico() {
        Usuario repetido = new Usuario("Otra Ana", "ana.garcia@correo.cl", "hash", null, Rol.CLIENTE);
        assertThatThrownBy(() -> usuarios.saveAndFlush(repetido)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
