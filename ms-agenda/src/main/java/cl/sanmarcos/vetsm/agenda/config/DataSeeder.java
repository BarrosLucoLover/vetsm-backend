package cl.sanmarcos.vetsm.agenda.config;

import cl.sanmarcos.vetsm.agenda.model.Cita;
import cl.sanmarcos.vetsm.agenda.model.EstadoCita;
import cl.sanmarcos.vetsm.agenda.model.Mascota;
import cl.sanmarcos.vetsm.agenda.repository.CitaRepository;
import cl.sanmarcos.vetsm.agenda.repository.MascotaRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Datos de demostración (solo en desarrollo, app.seed.demo=true): dos mascotas de la clienta
 * Ana García (id 3 en ms-usuarios) con una cita cada una. Los servicios usan los ids que crea
 * ms-catalogo (1 = Consulta General, 2 = Vacunación). No llama a otros servicios al arrancar:
 * así ms-agenda puede iniciar aunque los demás todavía no estén listos.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final long ID_CLIENTA_DEMO = 3L;
    private static final String TELEFONO_DEMO = "+56 9 8765 4321";

    private final MascotaRepository mascotas;
    private final CitaRepository citas;
    private final Clock reloj;

    @Value("${app.seed.demo:false}")
    private boolean demo;

    public DataSeeder(MascotaRepository mascotas, CitaRepository citas, Clock reloj) {
        this.mascotas = mascotas;
        this.citas = citas;
        this.reloj = reloj;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!demo || mascotas.count() > 0) {
            return;
        }
        Mascota pepe = mascotas.save(new Mascota("Pepe", "Perro", "Mestizo", ID_CLIENTA_DEMO, "Ana García", TELEFONO_DEMO));
        Mascota luna = mascotas.save(new Mascota("Luna", "Gato", "Siamés", ID_CLIENTA_DEMO, "Ana García", TELEFONO_DEMO));
        LocalDate hoy = LocalDate.now(reloj);
        citas.save(new Cita(pepe, 1L, "Consulta General", hoy.plusDays(1), LocalTime.of(9, 30), EstadoCita.CONFIRMADA, "Chequeo preventivo anual."));
        citas.save(new Cita(luna, 2L, "Vacunación", hoy.plusDays(2), LocalTime.of(11, 0), EstadoCita.PENDIENTE, "Refuerzo de vacuna."));
        log.info("Datos de demostración de la agenda creados.");
    }
}
