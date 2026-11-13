package cl.sanmarcos.vetsm.agenda;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Microservicio de agenda: citas de la clínica y mascotas (pacientes). */
@SpringBootApplication(scanBasePackages = {"cl.sanmarcos.vetsm.agenda", "cl.sanmarcos.vetsm.comun"})
public class MsAgendaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MsAgendaApplication.class, args);
    }
}
