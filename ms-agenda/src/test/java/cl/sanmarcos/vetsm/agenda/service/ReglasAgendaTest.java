package cl.sanmarcos.vetsm.agenda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cl.sanmarcos.vetsm.agenda.cliente.CatalogoCliente;
import cl.sanmarcos.vetsm.agenda.cliente.UsuariosCliente;
import cl.sanmarcos.vetsm.agenda.dto.CitaRequest;
import cl.sanmarcos.vetsm.agenda.dto.CitaResponse;
import cl.sanmarcos.vetsm.agenda.dto.MascotaRequest;
import cl.sanmarcos.vetsm.agenda.model.EstadoCita;
import cl.sanmarcos.vetsm.agenda.repository.MascotaRepository;
import cl.sanmarcos.vetsm.comun.exception.ApiException;
import cl.sanmarcos.vetsm.comun.security.Actor;
import cl.sanmarcos.vetsm.comun.security.Rol;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de la agenda contra la base H2 de ms-agenda (con las mascotas de demostración de Ana).
 * ms-catalogo y ms-usuarios se simulan (mock): se responde lo que ellos responderían.
 */
@SpringBootTest
@Transactional
class ReglasAgendaTest {

    private static final long ANA = 3L;
    private static final long CONSULTA = 1L;
    private static final long COLLAR = 9L;
    private static final Actor ANA_CLIENTE = new Actor(ANA, Rol.CLIENTE, "Ana García", "ana.garcia@correo.cl");
    private static final Actor PEDRO = new Actor(50L, Rol.CLIENTE, "Pedro Soto", "pedro@correo.cl");
    private static final Actor PERSONAL = new Actor(2L, Rol.RECEPCIONISTA, "Camila Rojas", "recepcion@sanmarcos.cl");

    @Autowired private CitaService citas;
    @Autowired private MascotaService mascotas;
    @Autowired private MascotaRepository mascotaRepo;
    @Autowired private Clock reloj;

    @MockitoBean private CatalogoCliente catalogo;
    @MockitoBean private UsuariosCliente usuarios;

    private LocalDate enUnaSemana;

    @BeforeEach
    void datos() {
        enUnaSemana = LocalDate.now(reloj).plusDays(7);
        when(catalogo.obtener(CONSULTA)).thenReturn(new CatalogoCliente.Producto(CONSULTA, "Consulta General", "SERVICIO", true));
        when(catalogo.obtener(COLLAR)).thenReturn(new CatalogoCliente.Producto(COLLAR, "Collar antipulgas", "PRODUCTO", true));
        when(usuarios.obtener(ANA)).thenReturn(new UsuariosCliente.Usuario(ANA, "Ana García", "+56 9 8765 4321", "CLIENTE", true));
        when(usuarios.obtener(2L)).thenReturn(new UsuariosCliente.Usuario(2L, "Camila Rojas", null, "RECEPCIONISTA", true));
    }

    private CitaRequest citaSinCuenta(LocalDate fecha, LocalTime hora) {
        return new CitaRequest(null, "Luna", "Pedro Soto", "+56 9 1122 3344", CONSULTA, fecha, hora, null);
    }

    private Long pepe() {
        return mascotaRepo.findByDuenoIdOrderByNombreAsc(ANA).stream()
                .filter(m -> m.getNombre().equals("Pepe")).findFirst().orElseThrow().getId();
    }

    @Nested
    @DisplayName("Citas")
    class Citas {

        @Test
        @DisplayName("Una solicitud sin cuenta queda PENDIENTE con los datos de contacto y el nombre del servicio")
        void solicitudSinCuenta() {
            CitaResponse cita = citas.crear(citaSinCuenta(enUnaSemana, LocalTime.of(10, 0)), null);
            assertThat(cita.estado()).isEqualTo(EstadoCita.PENDIENTE);
            assertThat(cita.mascotaId()).isNull();
            assertThat(cita.nombreDueno()).isEqualTo("Pedro Soto");
            assertThat(cita.servicioNombre()).isEqualTo("Consulta General");
        }

        @Test
        @DisplayName("Con una mascota registrada, los datos de contacto se toman de su dueño")
        void conMascotaRegistrada() {
            CitaRequest datos = new CitaRequest(pepe(), null, null, null, CONSULTA, enUnaSemana, LocalTime.of(12, 0), null);
            CitaResponse cita = citas.crear(datos, ANA_CLIENTE);
            assertThat(cita.nombreMascota()).isEqualTo("Pepe");
            assertThat(cita.nombreDueno()).isEqualTo("Ana García");
            assertThat(cita.telefonoContacto()).isEqualTo("+56 9 8765 4321");
        }

        @Test
        @DisplayName("Un cliente solo agenda para sus mascotas; sin sesión no se puede elegir una mascota registrada")
        void mascotaAjena() {
            CitaRequest conPepe = new CitaRequest(pepe(), null, null, null, CONSULTA, enUnaSemana, LocalTime.of(13, 0), null);
            assertThatThrownBy(() -> citas.crear(conPepe, PEDRO)).isInstanceOf(ApiException.NoEncontrado.class);
            assertThatThrownBy(() -> citas.crear(conPepe, null)).isInstanceOf(ApiException.ReglaNegocio.class);
            // El personal sí puede agendar para cualquier mascota.
            assertThat(citas.crear(conPepe, PERSONAL).nombreMascota()).isEqualTo("Pepe");
        }

        @Test
        @DisplayName("Sin mascota registrada exige nombre de la mascota, del dueño y teléfono")
        void exigeContacto() {
            CitaRequest incompleta = new CitaRequest(null, " ", null, null, CONSULTA, enUnaSemana, LocalTime.of(10, 0), null);
            assertThatThrownBy(() -> citas.crear(incompleta, null))
                    .isInstanceOf(ApiException.DatosInvalidos.class)
                    .satisfies(e -> assertThat(((ApiException) e).getErrores()).containsOnlyKeys("nombreMascota", "nombreDueno", "telefonoContacto"));
        }

        @Test
        @DisplayName("No se agenda en el pasado")
        void noEnElPasado() {
            assertThatThrownBy(() -> citas.crear(citaSinCuenta(LocalDate.now(reloj).minusDays(1), LocalTime.of(10, 0)), null))
                    .isInstanceOf(ApiException.ReglaNegocio.class)
                    .hasMessageContaining("ya pasó");
        }

        @Test
        @DisplayName("No se agenda en un horario ocupado, pero una cita cancelada libera el horario")
        void horarioOcupado() {
            CitaResponse primera = citas.crear(citaSinCuenta(enUnaSemana, LocalTime.of(10, 0)), null);
            assertThatThrownBy(() -> citas.crear(citaSinCuenta(enUnaSemana, LocalTime.of(10, 0)), null))
                    .isInstanceOf(ApiException.Conflicto.class);

            citas.cambiarEstado(primera.id(), EstadoCita.CANCELADA);
            assertThat(citas.crear(citaSinCuenta(enUnaSemana, LocalTime.of(10, 0)), null).id()).isNotNull();
        }

        @Test
        @DisplayName("Reagendar en el mismo horario no choca consigo misma")
        void reagendarMismaHora() {
            CitaResponse cita = citas.crear(citaSinCuenta(enUnaSemana, LocalTime.of(15, 0)), null);
            CitaRequest conNotas = new CitaRequest(null, "Luna", "Pedro Soto", "+56 9 1122 3344", CONSULTA, enUnaSemana,
                    LocalTime.of(15, 0), "Trae su carnet de vacunas.");
            assertThat(citas.actualizar(cita.id(), conNotas, PERSONAL).notas()).isEqualTo("Trae su carnet de vacunas.");
        }

        @Test
        @DisplayName("Solo se agendan servicios de la clínica (lo confirma ms-catalogo), no artículos de la tienda")
        void soloServicios() {
            CitaRequest conArticulo = new CitaRequest(null, "Luna", "Pedro", "+56 9 1122 3344", COLLAR, enUnaSemana, LocalTime.of(9, 0), null);
            assertThatThrownBy(() -> citas.crear(conArticulo, null)).isInstanceOf(ApiException.ReglaNegocio.class);
        }

        @Test
        @DisplayName("Si ms-catalogo no responde, la cita no se agenda (503)")
        void catalogoCaido() {
            when(catalogo.obtener(CONSULTA)).thenThrow(new ApiException.ServicioNoDisponible("catálogo"));
            assertThatThrownBy(() -> citas.crear(citaSinCuenta(enUnaSemana, LocalTime.of(9, 0)), null))
                    .isInstanceOf(ApiException.ServicioNoDisponible.class);
        }

        @Test
        @DisplayName("Los estados avanzan en orden y una cita completada no se modifica ni se elimina")
        void transiciones() {
            Long id = citas.crear(citaSinCuenta(enUnaSemana, LocalTime.of(16, 0)), null).id();
            assertThatThrownBy(() -> citas.cambiarEstado(id, EstadoCita.COMPLETADA)).isInstanceOf(ApiException.Conflicto.class);

            citas.cambiarEstado(id, EstadoCita.CONFIRMADA);
            assertThat(citas.cambiarEstado(id, EstadoCita.COMPLETADA).estado()).isEqualTo(EstadoCita.COMPLETADA);

            assertThatThrownBy(() -> citas.cambiarEstado(id, EstadoCita.CANCELADA)).isInstanceOf(ApiException.Conflicto.class);
            assertThatThrownBy(() -> citas.actualizar(id, citaSinCuenta(enUnaSemana, LocalTime.of(17, 0)), PERSONAL))
                    .isInstanceOf(ApiException.Conflicto.class);
            assertThatThrownBy(() -> citas.eliminar(id)).isInstanceOf(ApiException.Conflicto.class);
        }
    }

    @Nested
    @DisplayName("Mascotas")
    class Mascotas {

        @Test
        @DisplayName("Al registrar una mascota se copian el nombre y el teléfono del dueño que entrega ms-usuarios")
        void copiaDatosDelDueno() {
            var michi = mascotas.crear(new MascotaRequest("Michi", "Gato", null, ANA));
            assertThat(michi.duenoNombre()).isEqualTo("Ana García");
            assertThat(mascotaRepo.findById(michi.id()).orElseThrow().getDuenoTelefono()).isEqualTo("+56 9 8765 4321");
        }

        @Test
        @DisplayName("Una mascota solo se registra a nombre de un cliente, no del personal")
        void soloClientes() {
            assertThatThrownBy(() -> mascotas.crear(new MascotaRequest("Firulais", "Perro", null, 2L)))
                    .isInstanceOf(ApiException.ReglaNegocio.class);
        }

        @Test
        @DisplayName("Una mascota con citas no se elimina (es su historial)")
        void conCitas() {
            assertThatThrownBy(() -> mascotas.eliminar(pepe())).isInstanceOf(ApiException.Conflicto.class);
            assertThat(mascotas.contarDeDueno(ANA)).isEqualTo(2);
        }
    }
}
