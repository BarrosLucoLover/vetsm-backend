package cl.sanmarcos.vetsm.config;

import static org.assertj.core.api.Assertions.assertThat;

import cl.sanmarcos.vetsm.model.Producto;
import cl.sanmarcos.vetsm.model.Rol;
import cl.sanmarcos.vetsm.model.Usuario;
import cl.sanmarcos.vetsm.repository.CategoriaRepository;
import cl.sanmarcos.vetsm.repository.ProductoRepository;
import cl.sanmarcos.vetsm.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Levanta la aplicación completa sobre H2 y comprueba los datos iniciales. */
@SpringBootTest
class DataSeederTest {

    @Autowired private UsuarioRepository usuarios;
    @Autowired private CategoriaRepository categorias;
    @Autowired private ProductoRepository productos;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DataSeeder seeder;

    @Value("${app.seed.admin-password}")
    private String adminPassword;

    @Test
    @DisplayName("Se crean las cuentas del personal con la contraseña guardada como hash BCrypt")
    void cuentasDelPersonal() {
        Usuario admin = usuarios.findByEmail("admin@sanmarcos.cl").orElseThrow();
        assertThat(admin.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(admin.isActivo()).isTrue();
        assertThat(admin.getPasswordHash()).isNotEqualTo(adminPassword).startsWith("$2");
        assertThat(passwordEncoder.matches(adminPassword, admin.getPasswordHash())).isTrue();

        assertThat(usuarios.findByEmail("recepcion@sanmarcos.cl")).get().extracting(Usuario::getRol).isEqualTo(Rol.RECEPCIONISTA);
    }

    @Test
    @DisplayName("Se crea el catálogo: 4 categorías, 6 servicios sin stock y 6 artículos con stock")
    void catalogoInicial() {
        assertThat(categorias.count()).isEqualTo(4);
        assertThat(productos.findAll()).hasSize(12);
        assertThat(productos.findAll().stream().filter(Producto::esServicio)).hasSize(6).allMatch(p -> p.getStock() == null);
        assertThat(productos.findAll().stream().filter(p -> !p.esServicio())).hasSize(6).allMatch(p -> p.getStock() > 0);
    }

    @Test
    @DisplayName("Volver a ejecutar la carga inicial no duplica datos")
    void noDuplica() {
        long usuariosAntes = usuarios.count();
        seeder.run();
        assertThat(usuarios.count()).isEqualTo(usuariosAntes);
        assertThat(productos.count()).isEqualTo(12);
    }
}
