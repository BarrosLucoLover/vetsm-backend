package cl.sanmarcos.vetsm.config;

import cl.sanmarcos.vetsm.model.Categoria;
import cl.sanmarcos.vetsm.model.Cita;
import cl.sanmarcos.vetsm.model.EstadoCita;
import cl.sanmarcos.vetsm.model.Mascota;
import cl.sanmarcos.vetsm.model.Producto;
import cl.sanmarcos.vetsm.model.Rol;
import cl.sanmarcos.vetsm.model.Usuario;
import cl.sanmarcos.vetsm.repository.CategoriaRepository;
import cl.sanmarcos.vetsm.repository.CitaRepository;
import cl.sanmarcos.vetsm.repository.MascotaRepository;
import cl.sanmarcos.vetsm.repository.ProductoRepository;
import cl.sanmarcos.vetsm.repository.UsuarioRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga los datos iniciales la primera vez que arranca la aplicación sobre una base vacía:
 * las cuentas del personal, el catálogo de la tienda y, opcionalmente, datos de demostración.
 * Las contraseñas iniciales vienen de la configuración, nunca van escritas en el código.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UsuarioRepository usuarios;
    private final CategoriaRepository categorias;
    private final ProductoRepository productos;
    private final MascotaRepository mascotas;
    private final CitaRepository citas;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-password:}")
    private String adminPassword;

    @Value("${app.seed.recepcion-password:}")
    private String recepcionPassword;

    @Value("${app.seed.cliente-demo-password:}")
    private String clienteDemoPassword;

    public DataSeeder(UsuarioRepository usuarios, CategoriaRepository categorias, ProductoRepository productos,
            MascotaRepository mascotas, CitaRepository citas, PasswordEncoder passwordEncoder) {
        this.usuarios = usuarios;
        this.categorias = categorias;
        this.productos = productos;
        this.mascotas = mascotas;
        this.citas = citas;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        sembrarPersonal();
        sembrarCatalogo();
        sembrarClienteDemo();
    }

    private void sembrarPersonal() {
        crearUsuarioSiFalta("Administrador General", "admin@sanmarcos.cl", adminPassword, null, Rol.ADMIN);
        crearUsuarioSiFalta("Camila Rojas", "recepcion@sanmarcos.cl", recepcionPassword, "+56 9 5094 8714", Rol.RECEPCIONISTA);
    }

    private Usuario crearUsuarioSiFalta(String nombre, String email, String password, String telefono, Rol rol) {
        return usuarios.findByEmail(email).orElseGet(() -> {
            if (password == null || password.isBlank()) {
                log.warn("No se creó la cuenta inicial {}: falta configurar su contraseña (app.seed.*).", email);
                return null;
            }
            log.info("Creando cuenta inicial {} con rol {}.", email, rol);
            return usuarios.save(new Usuario(nombre, email, passwordEncoder.encode(password), telefono, rol));
        });
    }

    private void sembrarCatalogo() {
        if (categorias.count() > 0) {
            return;
        }
        Categoria clinica = categorias.save(new Categoria("Servicios clínicos", "Atenciones veterinarias que se agendan en la clínica."));
        Categoria alimentos = categorias.save(new Categoria("Alimentos", "Alimento seco y húmedo para perros y gatos."));
        Categoria accesorios = categorias.save(new Categoria("Accesorios", "Collares, juguetes y artículos de paseo."));
        Categoria higiene = categorias.save(new Categoria("Higiene y cuidado", "Productos de aseo y cuidado diario."));

        productos.saveAll(List.of(
                Producto.servicio("Consulta General", "Evaluación clínica completa y orientación al dueño.", 25000, 30, clinica),
                Producto.servicio("Vacunación", "Aplicación de vacunas según el calendario de la mascota.", 18000, 20, clinica),
                Producto.servicio("Laboratorio y Exámenes", "Toma de muestras y exámenes de laboratorio.", 35000, 45, clinica),
                Producto.servicio("Odontología", "Limpieza dental y revisión de encías.", 40000, 60, clinica),
                Producto.servicio("Cirugía", "Procedimientos quirúrgicos programados.", 120000, 120, clinica),
                Producto.servicio("Urgencias 24h", "Atención inmediata fuera de horario.", 50000, 45, clinica),
                Producto.articulo("Alimento premium perro adulto 15 kg", "Alimento seco balanceado para perros adultos.", 42990, 20, alimentos),
                Producto.articulo("Alimento gato esterilizado 7,5 kg", "Alimento seco para gatos esterilizados.", 31990, 15, alimentos),
                Producto.articulo("Collar antipulgas", "Protección contra pulgas y garrapatas por 8 meses.", 12990, 30, accesorios),
                Producto.articulo("Juguete mordedor", "Mordedor de goma resistente para perros.", 5990, 40, accesorios),
                Producto.articulo("Shampoo hipoalergénico 500 ml", "Shampoo suave para pieles sensibles.", 8990, 25, higiene),
                Producto.articulo("Arena sanitaria 10 kg", "Arena aglomerante de bajo polvo.", 9990, 18, higiene)));
        log.info("Catálogo inicial creado: {} categorías y {} productos.", categorias.count(), productos.count());
    }

    /** Cliente con mascotas y citas de ejemplo. Solo se crea si se configura su contraseña (perfil de desarrollo). */
    private void sembrarClienteDemo() {
        String email = "ana.garcia@correo.cl";
        if (clienteDemoPassword == null || clienteDemoPassword.isBlank() || usuarios.existsByEmail(email)) {
            return;
        }
        Usuario ana = usuarios.save(new Usuario("Ana García", email, passwordEncoder.encode(clienteDemoPassword), "+56 9 8765 4321", Rol.CLIENTE));
        Mascota pepe = mascotas.save(new Mascota("Pepe", "Perro", "Mestizo", ana));
        Mascota luna = mascotas.save(new Mascota("Luna", "Gato", "Siamés", ana));

        List<Producto> servicios = productos.findAll().stream().filter(Producto::esServicio).toList();
        if (servicios.size() < 2) {
            return;
        }
        LocalDate hoy = LocalDate.now();
        citas.save(new Cita(pepe, servicios.get(0), hoy.plusDays(1), LocalTime.of(9, 30), EstadoCita.CONFIRMADA, "Chequeo preventivo anual."));
        citas.save(new Cita(luna, servicios.get(1), hoy.plusDays(2), LocalTime.of(11, 0), EstadoCita.PENDIENTE, "Refuerzo de vacuna."));
        log.info("Datos de demostración creados para {}.", email);
    }
}
