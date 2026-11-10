package cl.sanmarcos.vetsm.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.sanmarcos.vetsm.model.Boleta;
import cl.sanmarcos.vetsm.model.Categoria;
import cl.sanmarcos.vetsm.model.Cita;
import cl.sanmarcos.vetsm.model.EstadoBoleta;
import cl.sanmarcos.vetsm.model.EstadoCita;
import cl.sanmarcos.vetsm.model.Mascota;
import cl.sanmarcos.vetsm.model.Producto;
import cl.sanmarcos.vetsm.model.Rol;
import cl.sanmarcos.vetsm.model.TipoProducto;
import cl.sanmarcos.vetsm.model.Usuario;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

/** Pruebas de integración de los repositorios contra una base H2 en memoria. */
@DataJpaTest
class RepositoriosTest {

    @Autowired private UsuarioRepository usuarios;
    @Autowired private MascotaRepository mascotas;
    @Autowired private CategoriaRepository categorias;
    @Autowired private ProductoRepository productos;
    @Autowired private CitaRepository citas;
    @Autowired private BoletaRepository boletas;
    @Autowired private TestEntityManager em;

    private Usuario ana;
    private Usuario pedro;
    private Mascota pepe;
    private Producto consulta;
    private Producto collar;

    @BeforeEach
    void datosBase() {
        ana = usuarios.save(new Usuario("Ana García", "Ana.Garcia@Correo.CL ", "hash", null, Rol.CLIENTE));
        pedro = usuarios.save(new Usuario("Pedro Soto", "pedro@correo.cl", "hash", null, Rol.CLIENTE));
        pepe = mascotas.save(new Mascota("Pepe", "Perro", null, ana));
        mascotas.save(new Mascota("Luna", "Gato", null, pedro));

        Categoria clinica = categorias.save(new Categoria("Servicios clínicos", null));
        Categoria accesorios = categorias.save(new Categoria("Accesorios", null));
        consulta = productos.save(Producto.servicio("Consulta General", null, 25000, 30, clinica));
        collar = productos.save(Producto.articulo("Collar antipulgas", null, 12990, 5, accesorios));
        em.flush();
    }

    @Test
    @DisplayName("El correo se guarda en minúsculas y sin espacios, y permite buscar al usuario")
    void correoNormalizado() {
        assertThat(usuarios.findByEmail("ana.garcia@correo.cl")).isPresent();
        assertThat(usuarios.existsByEmail("ana.garcia@correo.cl")).isTrue();
        assertThat(usuarios.existsByEmail("nadie@correo.cl")).isFalse();
        assertThat(usuarios.findByRol(Rol.CLIENTE)).hasSize(2);
    }

    @Test
    @DisplayName("La base de datos rechaza dos usuarios con el mismo correo")
    void correoUnico() {
        Usuario repetido = new Usuario("Otra Ana", "ANA.GARCIA@correo.cl", "hash", null, Rol.CLIENTE);
        assertThatThrownBy(() -> usuarios.saveAndFlush(repetido)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("Cada dueño solo obtiene sus propias mascotas")
    void mascotasPorDueno() {
        assertThat(mascotas.findByDuenoIdOrderByNombreAsc(ana.getId())).extracting(Mascota::getNombre).containsExactly("Pepe");
        assertThat(mascotas.findByDuenoIdOrderByNombreAsc(pedro.getId())).extracting(Mascota::getNombre).containsExactly("Luna");
    }

    @Test
    @DisplayName("El catálogo filtra por tipo y oculta los productos inactivos")
    void catalogo() {
        assertThat(productos.findByTipoAndActivoTrueOrderByNombreAsc(TipoProducto.SERVICIO)).extracting(Producto::getNombre)
                .containsExactly("Consulta General");

        collar.setActivo(false);
        productos.saveAndFlush(collar);

        assertThat(productos.findByActivoTrueOrderByNombreAsc()).extracting(Producto::getNombre).containsExactly("Consulta General");
        assertThat(productos.existsByCategoriaId(collar.getCategoria().getId())).isTrue();
    }

    @Test
    @DisplayName("Un horario queda ocupado por una cita vigente y se libera si la cita se cancela")
    void horarioOcupado() {
        LocalDate fecha = LocalDate.now().plusDays(3);
        LocalTime hora = LocalTime.of(10, 0);
        Cita cita = citas.saveAndFlush(new Cita(pepe, consulta, fecha, hora, EstadoCita.PENDIENTE, null));

        assertThat(citas.existsByFechaAndHoraAndEstadoNot(fecha, hora, EstadoCita.CANCELADA)).isTrue();
        assertThat(citas.existsByFechaAndHoraAndEstadoNot(fecha, hora.plusMinutes(30), EstadoCita.CANCELADA)).isFalse();
        // Al reagendar la misma cita, su propio horario no cuenta como ocupado.
        assertThat(citas.existsByFechaAndHoraAndEstadoNotAndIdNot(fecha, hora, EstadoCita.CANCELADA, cita.getId())).isFalse();

        cita.setEstado(EstadoCita.CANCELADA);
        citas.saveAndFlush(cita);

        assertThat(citas.existsByFechaAndHoraAndEstadoNot(fecha, hora, EstadoCita.CANCELADA)).isFalse();
    }

    @Test
    @DisplayName("Un cliente solo obtiene las citas de sus mascotas")
    void citasPorDueno() {
        citas.saveAndFlush(new Cita(pepe, consulta, LocalDate.now().plusDays(1), LocalTime.of(9, 0), EstadoCita.PENDIENTE, null));

        assertThat(citas.findByMascotaDuenoIdOrderByFechaDescHoraDesc(ana.getId())).hasSize(1);
        assertThat(citas.findByMascotaDuenoIdOrderByFechaDescHoraDesc(pedro.getId())).isEmpty();
        assertThat(citas.existsByMascotaId(pepe.getId())).isTrue();
    }

    @Test
    @DisplayName("Al guardar una boleta se guardan también sus detalles, con fecha, estado y total")
    void boletaConDetalles() {
        Boleta boleta = new Boleta(ana);
        boleta.agregarDetalle(consulta, 1);
        boleta.agregarDetalle(collar, 2);
        Long id = boletas.saveAndFlush(boleta).getId();
        em.clear();

        Boleta guardada = boletas.findById(id).orElseThrow();
        assertThat(guardada.getFecha()).isNotNull();
        assertThat(guardada.getEstado()).isEqualTo(EstadoBoleta.PAGADA);
        assertThat(guardada.getTotal()).isEqualTo(50980);
        assertThat(guardada.getDetalles()).hasSize(2);
        assertThat(boletas.findByClienteIdOrderByFechaDesc(ana.getId())).hasSize(1);
        assertThat(boletas.findByClienteIdOrderByFechaDesc(pedro.getId())).isEmpty();
    }
}
