package cl.sanmarcos.vetsm.catalogo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Microservicio de catálogo: categorías, productos, servicios de la clínica e inventario (stock). */
@SpringBootApplication(scanBasePackages = {"cl.sanmarcos.vetsm.catalogo", "cl.sanmarcos.vetsm.comun"})
public class MsCatalogoApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsCatalogoApplication.class, args);
    }
}
