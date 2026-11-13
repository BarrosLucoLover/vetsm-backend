package cl.sanmarcos.vetsm.usuarios;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Microservicio de usuarios: cuentas, inicio de sesión y perfil. Es el único que EMITE los tokens
 * JWT; los demás servicios solo los verifican.
 */
@SpringBootApplication(scanBasePackages = {"cl.sanmarcos.vetsm.usuarios", "cl.sanmarcos.vetsm.comun"})
public class MsUsuariosApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsUsuariosApplication.class, args);
    }
}
