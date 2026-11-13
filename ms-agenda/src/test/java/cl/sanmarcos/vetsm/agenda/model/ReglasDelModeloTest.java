package cl.sanmarcos.vetsm.agenda.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pruebas unitarias de las reglas que viven en las propias entidades (no necesitan base de datos). */
class ReglasDelModeloTest {

    @Test
    @DisplayName("Una cita pendiente solo puede confirmarse o cancelarse")
    void transicionesDesdePendiente() {
        assertThat(EstadoCita.PENDIENTE.puedePasarA(EstadoCita.CONFIRMADA)).isTrue();
        assertThat(EstadoCita.PENDIENTE.puedePasarA(EstadoCita.CANCELADA)).isTrue();
        assertThat(EstadoCita.PENDIENTE.puedePasarA(EstadoCita.COMPLETADA)).isFalse();
    }

    @Test
    @DisplayName("Una cita confirmada puede completarse o cancelarse, pero no volver a pendiente")
    void transicionesDesdeConfirmada() {
        assertThat(EstadoCita.CONFIRMADA.puedePasarA(EstadoCita.COMPLETADA)).isTrue();
        assertThat(EstadoCita.CONFIRMADA.puedePasarA(EstadoCita.CANCELADA)).isTrue();
        assertThat(EstadoCita.CONFIRMADA.puedePasarA(EstadoCita.PENDIENTE)).isFalse();
    }

    @Test
    @DisplayName("Las citas completadas o canceladas son estados finales")
    void estadosFinales() {
        assertThat(EstadoCita.COMPLETADA.esFinal()).isTrue();
        assertThat(EstadoCita.CANCELADA.esFinal()).isTrue();
        assertThat(EstadoCita.PENDIENTE.esFinal()).isFalse();
        assertThat(EstadoCita.COMPLETADA.siguientesPermitidos()).isEmpty();
    }

    @Test
    @DisplayName("Una cita de una mascota registrada copia el nombre de la mascota y los datos de contacto del dueño")
    void copiaDatosDeLaMascota() {
        Mascota pepe = new Mascota("Pepe", "Perro", null, 3L, "Ana García", "+56 9 8765 4321");
        Cita cita = new Cita(pepe, 1L, "Consulta General", LocalDate.now(), LocalTime.NOON, EstadoCita.PENDIENTE, null);
        assertThat(cita.getNombreMascota()).isEqualTo("Pepe");
        assertThat(cita.getNombreDueno()).isEqualTo("Ana García");
        assertThat(cita.getTelefonoContacto()).isEqualTo("+56 9 8765 4321");
        assertThat(cita.getServicioNombre()).isEqualTo("Consulta General");
    }
}
