package cl.sanmarcos.vetsm.usuarios.config;

import cl.sanmarcos.vetsm.comun.security.Rol;
import cl.sanmarcos.vetsm.usuarios.model.Usuario;
import cl.sanmarcos.vetsm.usuarios.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea las cuentas iniciales la primera vez que arranca el servicio sobre una base vacía: el
 * personal y, en desarrollo, la clienta de demostración. Las contraseñas vienen de la configuración,
 * nunca van escritas en el código. El orden de creación fija los ids (1 admin, 2 recepción,
 * 3 clienta), que ms-agenda usa para sus datos de demostración.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-password:}")
    private String adminPassword;

    @Value("${app.seed.recepcion-password:}")
    private String recepcionPassword;

    @Value("${app.seed.cliente-demo-password:}")
    private String clienteDemoPassword;

    public DataSeeder(UsuarioRepository usuarios, PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        crearUsuarioSiFalta("Administrador General", "admin@sanmarcos.cl", adminPassword, null, Rol.ADMIN);
        crearUsuarioSiFalta("Camila Rojas", "recepcion@sanmarcos.cl", recepcionPassword, "+56 9 5094 8714", Rol.RECEPCIONISTA);
        crearUsuarioSiFalta("Ana García", "ana.garcia@correo.cl", clienteDemoPassword, "+56 9 8765 4321", Rol.CLIENTE);
    }

    private void crearUsuarioSiFalta(String nombre, String email, String password, String telefono, Rol rol) {
        if (usuarios.existsByEmail(email)) {
            return;
        }
        if (password == null || password.isBlank()) {
            log.warn("No se creó la cuenta inicial {}: falta configurar su contraseña (app.seed.*).", email);
            return;
        }
        log.info("Creando cuenta inicial {} con rol {}.", email, rol);
        usuarios.save(new Usuario(nombre, email, passwordEncoder.encode(password), telefono, rol));
    }
}
