package cl.sanmarcos.vetsm.catalogo.config;

import cl.sanmarcos.vetsm.catalogo.model.Categoria;
import cl.sanmarcos.vetsm.catalogo.model.Producto;
import cl.sanmarcos.vetsm.catalogo.repository.CategoriaRepository;
import cl.sanmarcos.vetsm.catalogo.repository.ProductoRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el catálogo inicial la primera vez que arranca el servicio sobre una base vacía.
 * El orden fija los ids: los servicios de la clínica son del 1 al 6 y los artículos del 7 al 12.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final CategoriaRepository categorias;
    private final ProductoRepository productos;

    public DataSeeder(CategoriaRepository categorias, ProductoRepository productos) {
        this.categorias = categorias;
        this.productos = productos;
    }

    @Override
    @Transactional
    public void run(String... args) {
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
}
