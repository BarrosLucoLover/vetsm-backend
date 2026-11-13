package cl.sanmarcos.vetsm.ventas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Microservicio de ventas: compras (boletas), pago simulado y tarjetas guardadas. */
@SpringBootApplication(scanBasePackages = {"cl.sanmarcos.vetsm.ventas", "cl.sanmarcos.vetsm.comun"})
public class MsVentasApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsVentasApplication.class, args);
    }
}
