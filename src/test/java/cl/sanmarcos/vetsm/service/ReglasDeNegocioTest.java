package cl.sanmarcos.vetsm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cl.sanmarcos.vetsm.dto.BoletaResponse;
import cl.sanmarcos.vetsm.dto.CambioPasswordRequest;
import cl.sanmarcos.vetsm.dto.CitaRequest;
import cl.sanmarcos.vetsm.dto.CitaResponse;
import cl.sanmarcos.vetsm.dto.CompraRequest;
import cl.sanmarcos.vetsm.dto.ItemCompraRequest;
import cl.sanmarcos.vetsm.dto.PagoRequest;
import cl.sanmarcos.vetsm.dto.ProductoRequest;
import cl.sanmarcos.vetsm.dto.TarjetaRequest;
import cl.sanmarcos.vetsm.dto.UsuarioRequest;
import cl.sanmarcos.vetsm.dto.UsuarioUpdateRequest;
import cl.sanmarcos.vetsm.exception.ApiException;
import cl.sanmarcos.vetsm.model.EstadoBoleta;
import cl.sanmarcos.vetsm.model.EstadoCita;
import cl.sanmarcos.vetsm.model.MarcaTarjeta;
import cl.sanmarcos.vetsm.model.Producto;
import cl.sanmarcos.vetsm.model.Rol;
import cl.sanmarcos.vetsm.model.TipoProducto;
import cl.sanmarcos.vetsm.model.Usuario;
import cl.sanmarcos.vetsm.repository.MascotaRepository;
import cl.sanmarcos.vetsm.repository.ProductoRepository;
import cl.sanmarcos.vetsm.repository.TarjetaRepository;
import cl.sanmarcos.vetsm.repository.UsuarioRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pruebas de integración de las reglas de negocio, contra la base H2 con los datos iniciales.
 * Cada prueba corre en una transacción que se deshace al terminar: no se afectan entre sí.
 */
@SpringBootTest
@Transactional
class ReglasDeNegocioTest {

    private static final TarjetaRequest VISA = new TarjetaRequest("4111 1111 1111 1111", "Ana García", "12/35");
    private static final TarjetaRequest RECHAZADA = new TarjetaRequest("4000 0000 0000 0002", "Ana García", "12/35");

    @Autowired private CitaService citas;
    @Autowired private BoletaService boletas;
    @Autowired private UsuarioService usuarios;
    @Autowired private ProductoService productos;
    @Autowired private CategoriaService categorias;
    @Autowired private TarjetaService tarjetas;
    @Autowired private UsuarioRepository usuarioRepo;
    @Autowired private ProductoRepository productoRepo;
    @Autowired private MascotaRepository mascotaRepo;
    @Autowired private TarjetaRepository tarjetaRepo;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private Clock reloj;

    private Usuario ana;
    private Usuario admin;
    private Producto consulta;
    private Producto collar;
    private LocalDate enUnaSemana;

    @BeforeEach
    void datos() {
        ana = usuarioRepo.findByEmail("ana.garcia@correo.cl").orElseThrow();
        admin = usuarioRepo.findByEmail("admin@sanmarcos.cl").orElseThrow();
        consulta = porNombre("Consulta General");
        collar = porNombre("Collar antipulgas");
        enUnaSemana = LocalDate.now(reloj).plusDays(7);
    }

    private Producto porNombre(String nombre) {
        return productoRepo.findAll().stream().filter(p -> p.getNombre().equals(nombre)).findFirst().orElseThrow();
    }

    private CitaRequest citaSinCuenta(LocalDate fecha, LocalTime hora) {
        return new CitaRequest(null, "Luna", "Pedro Soto", "+56 9 1122 3344", consulta.getId(), fecha, hora, null);
    }

    private CompraRequest compra(Long productoId, int cantidad, PagoRequest pago) {
        return new CompraRequest(ana.getId(), List.of(new ItemCompraRequest(productoId, cantidad)), pago);
    }

    private static PagoRequest pagoNuevo(TarjetaRequest tarjeta, boolean guardar) {
        return new PagoRequest(null, tarjeta, "123", guardar);
    }

    @Nested
    @DisplayName("Citas")
    class Citas {

        @Test
        @DisplayName("Una solicitud sin cuenta queda PENDIENTE con los datos de contacto")
        void solicitudSinCuenta() {
            CitaResponse cita = citas.crear(citaSinCuenta(enUnaSemana, LocalTime.of(10, 0)));
            assertThat(cita.estado()).isEqualTo(EstadoCita.PENDIENTE);
            assertThat(cita.mascotaId()).isNull();
            assertThat(cita.nombreDueno()).isEqualTo("Pedro Soto");
        }

        @Test
        @DisplayName("Con una mascota registrada, los datos de contacto se toman de su dueño")
        void conMascotaRegistrada() {
            Long pepe = mascotaRepo.findByDuenoIdOrderByNombreAsc(ana.getId()).stream()
                    .filter(m -> m.getNombre().equals("Pepe")).findFirst().orElseThrow().getId();
            CitaRequest datos = new CitaRequest(pepe, null, null, null, consulta.getId(), enUnaSemana, LocalTime.of(12, 0), null);
            CitaResponse cita = citas.crear(datos);
            assertThat(cita.nombreMascota()).isEqualTo("Pepe");
            assertThat(cita.nombreDueno()).isEqualTo("Ana García");
            assertThat(cita.telefonoContacto()).isEqualTo("+56 9 8765 4321");
        }

        @Test
        @DisplayName("Sin mascota registrada exige nombre de la mascota, del dueño y teléfono")
        void exigeContacto() {
            CitaRequest incompleta = new CitaRequest(null, " ", null, null, consulta.getId(), enUnaSemana, LocalTime.of(10, 0), null);
            assertThatThrownBy(() -> citas.crear(incompleta))
                    .isInstanceOf(ApiException.DatosInvalidos.class)
                    .satisfies(e -> assertThat(((ApiException) e).getErrores()).containsOnlyKeys("nombreMascota", "nombreDueno", "telefonoContacto"));
        }

        @Test
        @DisplayName("No se agenda en el pasado")
        void noEnElPasado() {
            assertThatThrownBy(() -> citas.crear(citaSinCuenta(LocalDate.now(reloj).minusDays(1), LocalTime.of(10, 0))))
                    .isInstanceOf(ApiException.ReglaNegocio.class)
                    .hasMessageContaining("ya pasó");
        }

        @Test
        @DisplayName("No se agenda en un horario ocupado, pero una cita cancelada libera el horario")
        void horarioOcupado() {
            CitaResponse primera = citas.crear(citaSinCuenta(enUnaSemana, LocalTime.of(10, 0)));
            assertThatThrownBy(() -> citas.crear(citaSinCuenta(enUnaSemana, LocalTime.of(10, 0))))
                    .isInstanceOf(ApiException.Conflicto.class);

            citas.cambiarEstado(primera.id(), EstadoCita.CANCELADA);
            assertThat(citas.crear(citaSinCuenta(enUnaSemana, LocalTime.of(10, 0))).id()).isNotNull();
        }

        @Test
        @DisplayName("Reagendar en el mismo horario no choca consigo misma")
        void reagendarMismaHora() {
            CitaResponse cita = citas.crear(citaSinCuenta(enUnaSemana, LocalTime.of(15, 0)));
            CitaRequest conNotas = new CitaRequest(null, "Luna", "Pedro Soto", "+56 9 1122 3344", consulta.getId(), enUnaSemana,
                    LocalTime.of(15, 0), "Trae su carnet de vacunas.");
            assertThat(citas.actualizar(cita.id(), conNotas).notas()).isEqualTo("Trae su carnet de vacunas.");
        }

        @Test
        @DisplayName("Solo se agendan servicios de la clínica, no artículos de la tienda")
        void soloServicios() {
            CitaRequest conArticulo = new CitaRequest(null, "Luna", "Pedro", "+56 9 1122 3344", collar.getId(), enUnaSemana, LocalTime.of(9, 0), null);
            assertThatThrownBy(() -> citas.crear(conArticulo)).isInstanceOf(ApiException.ReglaNegocio.class);
        }

        @Test
        @DisplayName("Los estados avanzan en orden y una cita completada no se modifica ni se elimina")
        void transiciones() {
            Long id = citas.crear(citaSinCuenta(enUnaSemana, LocalTime.of(16, 0))).id();
            assertThatThrownBy(() -> citas.cambiarEstado(id, EstadoCita.COMPLETADA)).isInstanceOf(ApiException.Conflicto.class);

            citas.cambiarEstado(id, EstadoCita.CONFIRMADA);
            assertThat(citas.cambiarEstado(id, EstadoCita.COMPLETADA).estado()).isEqualTo(EstadoCita.COMPLETADA);

            assertThatThrownBy(() -> citas.cambiarEstado(id, EstadoCita.CANCELADA)).isInstanceOf(ApiException.Conflicto.class);
            assertThatThrownBy(() -> citas.actualizar(id, citaSinCuenta(enUnaSemana, LocalTime.of(17, 0)))).isInstanceOf(ApiException.Conflicto.class);
            assertThatThrownBy(() -> citas.eliminar(id)).isInstanceOf(ApiException.Conflicto.class);
        }
    }

    @Nested
    @DisplayName("Compras y pagos")
    class Compras {

        @Test
        @DisplayName("Una compra aprobada crea la boleta con precios del servidor y descuenta el stock")
        void compraAprobada() {
            int stockAntes = collar.getStock();
            CompraRequest datos = new CompraRequest(ana.getId(),
                    List.of(new ItemCompraRequest(collar.getId(), 2), new ItemCompraRequest(consulta.getId(), 1)), pagoNuevo(VISA, false));

            BoletaResponse boleta = boletas.comprar(datos);

            assertThat(boleta.total()).isEqualTo(2 * 12990 + 25000);
            assertThat(boleta.estado()).isEqualTo(EstadoBoleta.PAGADA);
            assertThat(boleta.medioPago().marca()).isEqualTo(MarcaTarjeta.VISA);
            assertThat(boleta.medioPago().ultimos4()).isEqualTo("1111");
            assertThat(boleta.medioPago().codigoAutorizacion()).matches("\\d{6}");
            assertThat(productoRepo.findById(collar.getId()).orElseThrow().getStock()).isEqualTo(stockAntes - 2);
        }

        @Test
        @DisplayName("Si el mismo producto viene dos veces en el carrito, se suma en una sola línea")
        void sumaRepetidos() {
            CompraRequest datos = new CompraRequest(ana.getId(),
                    List.of(new ItemCompraRequest(collar.getId(), 1), new ItemCompraRequest(collar.getId(), 2)), pagoNuevo(VISA, false));
            BoletaResponse boleta = boletas.comprar(datos);
            assertThat(boleta.detalles()).hasSize(1);
            assertThat(boleta.detalles().get(0).cantidad()).isEqualTo(3);
        }

        @Test
        @DisplayName("No se vende más que el stock disponible")
        void sinStock() {
            assertThatThrownBy(() -> boletas.comprar(compra(collar.getId(), collar.getStock() + 1, pagoNuevo(VISA, false))))
                    .isInstanceOf(ApiException.Conflicto.class)
                    .hasMessageContaining("Stock insuficiente");
        }

        @Test
        @DisplayName("Si el banco rechaza la tarjeta no se descuenta stock")
        void tarjetaRechazada() {
            int stockAntes = collar.getStock();
            assertThatThrownBy(() -> boletas.comprar(compra(collar.getId(), 1, pagoNuevo(RECHAZADA, false))))
                    .isInstanceOf(ApiException.PagoRechazado.class);
            assertThat(productoRepo.findById(collar.getId()).orElseThrow().getStock()).isEqualTo(stockAntes);
        }

        @Test
        @DisplayName("Una tarjeta mal escrita se rechaza con el error por campo")
        void tarjetaInvalida() {
            TarjetaRequest malEscrita = new TarjetaRequest("4111 1111 1111 1112", "Ana", "12/35");
            assertThatThrownBy(() -> boletas.comprar(compra(collar.getId(), 1, pagoNuevo(malEscrita, false))))
                    .isInstanceOf(ApiException.DatosInvalidos.class)
                    .satisfies(e -> assertThat(((ApiException) e).getErrores()).containsKey("tarjeta.numero"));
        }

        @Test
        @DisplayName("Al guardar la tarjeta no se almacena el número completo ni el CVV")
        void guardaSoloDatosSeguros() {
            boletas.comprar(compra(consulta.getId(), 1, pagoNuevo(VISA, true)));
            var guardada = tarjetaRepo.findByUsuarioIdOrderByPredeterminadaDescIdDesc(ana.getId()).get(0);
            assertThat(guardada.getUltimos4()).isEqualTo("1111");
            assertThat(guardada.isPredeterminada()).isTrue();
            assertThat(guardada.toString()).doesNotContain("4111111111111111");
        }

        @Test
        @DisplayName("Con una tarjeta guardada solo se pide el CVV; no se puede usar la tarjeta de otro cliente")
        void tarjetaGuardada() {
            Long id = tarjetas.guardar(ana, VISA).tarjeta().id();
            BoletaResponse boleta = boletas.comprar(compra(consulta.getId(), 1, new PagoRequest(id, null, "456", false)));
            assertThat(boleta.medioPago().ultimos4()).isEqualTo("1111");

            Usuario otro = usuarioRepo.save(new Usuario("Pedro Soto", "pedro@correo.cl", "hash", null, Rol.CLIENTE));
            CompraRequest ajena = new CompraRequest(otro.getId(), List.of(new ItemCompraRequest(consulta.getId(), 1)),
                    new PagoRequest(id, null, "456", false));
            assertThatThrownBy(() -> boletas.comprar(ajena)).isInstanceOf(ApiException.NoEncontrado.class);
        }

        @Test
        @DisplayName("Hay que elegir una tarjeta guardada o una nueva, no ambas")
        void unSoloMedioDePago() {
            assertThatThrownBy(() -> boletas.comprar(compra(consulta.getId(), 1, new PagoRequest(null, null, "123", false))))
                    .isInstanceOf(ApiException.DatosInvalidos.class);
        }

        @Test
        @DisplayName("El personal no compra con su cuenta y no se venden productos dados de baja")
        void restricciones() {
            CompraRequest delAdmin = new CompraRequest(admin.getId(), List.of(new ItemCompraRequest(consulta.getId(), 1)), pagoNuevo(VISA, false));
            assertThatThrownBy(() -> boletas.comprar(delAdmin)).isInstanceOf(ApiException.ReglaNegocio.class);

            productos.darDeBaja(collar.getId());
            assertThatThrownBy(() -> boletas.comprar(compra(collar.getId(), 1, pagoNuevo(VISA, false))))
                    .isInstanceOf(ApiException.ReglaNegocio.class)
                    .hasMessageContaining("ya no está disponible");
        }

        @Test
        @DisplayName("Anular una compra devuelve el stock y no se puede anular dos veces")
        void anular() {
            int stockAntes = collar.getStock();
            Long id = boletas.comprar(compra(collar.getId(), 3, pagoNuevo(VISA, false))).id();
            assertThat(boletas.anular(id).estado()).isEqualTo(EstadoBoleta.ANULADA);
            assertThat(productoRepo.findById(collar.getId()).orElseThrow().getStock()).isEqualTo(stockAntes);
            assertThatThrownBy(() -> boletas.anular(id)).isInstanceOf(ApiException.Conflicto.class);
        }
    }

    @Nested
    @DisplayName("Usuarios")
    class Usuarios {

        @Test
        @DisplayName("El correo se normaliza y no se puede repetir")
        void correoUnico() {
            var creado = usuarios.crear(new UsuarioRequest("Javiera Torres", " Javiera@SanMarcos.cl ", "Clave1234", null, Rol.RECEPCIONISTA));
            assertThat(creado.email()).isEqualTo("javiera@sanmarcos.cl");
            assertThatThrownBy(() -> usuarios.crear(new UsuarioRequest("Otra", "JAVIERA@sanmarcos.cl", "Clave1234", null, Rol.CLIENTE)))
                    .isInstanceOf(ApiException.Conflicto.class);
        }

        @Test
        @DisplayName("La contraseña se guarda con hash BCrypt")
        void passwordConHash() {
            var creado = usuarios.crear(new UsuarioRequest("Javiera Torres", "javiera@sanmarcos.cl", "Clave1234", null, Rol.RECEPCIONISTA));
            String hash = usuarioRepo.findById(creado.id()).orElseThrow().getPasswordHash();
            assertThat(hash).isNotEqualTo("Clave1234").startsWith("$2");
            assertThat(passwordEncoder.matches("Clave1234", hash)).isTrue();
        }

        @Test
        @DisplayName("El registro público siempre crea un cliente")
        void registroCreaCliente() {
            assertThat(usuarios.registrarCliente("Luc Pérez", "luc@correo.cl", "Clave1234").rol()).isEqualTo(Rol.CLIENTE);
        }

        @Test
        @DisplayName("El único administrador activo no se puede desactivar, eliminar ni degradar")
        void ultimoAdministrador() {
            Long id = admin.getId();
            assertThatThrownBy(() -> usuarios.cambiarActivo(id, false)).isInstanceOf(ApiException.ReglaNegocio.class);
            assertThatThrownBy(() -> usuarios.eliminar(id)).isInstanceOf(ApiException.ReglaNegocio.class);
            UsuarioUpdateRequest degradar = new UsuarioUpdateRequest(admin.getNombre(), admin.getEmail(), null, Rol.CLIENTE);
            assertThatThrownBy(() -> usuarios.actualizar(id, degradar)).isInstanceOf(ApiException.ReglaNegocio.class);

            usuarios.crear(new UsuarioRequest("Segundo Admin", "admin2@sanmarcos.cl", "Clave1234", null, Rol.ADMIN));
            assertThat(usuarios.cambiarActivo(id, false).activo()).isFalse();
        }

        @Test
        @DisplayName("Una cuenta con historial no se elimina: hay que desactivarla")
        void conHistorial() {
            assertThatThrownBy(() -> usuarios.eliminar(ana.getId()))
                    .isInstanceOf(ApiException.Conflicto.class)
                    .hasMessageContaining("Desactívala");
        }

        @Test
        @DisplayName("Cambiar la contraseña exige la actual correcta y una nueva distinta")
        void cambiarPassword() {
            assertThatThrownBy(() -> usuarios.cambiarPassword(ana.getId(), new CambioPasswordRequest("Equivocada1", "NuevaClave1")))
                    .isInstanceOf(ApiException.DatosInvalidos.class);
            assertThatThrownBy(() -> usuarios.cambiarPassword(ana.getId(), new CambioPasswordRequest("Cliente123!", "Cliente123!")))
                    .isInstanceOf(ApiException.DatosInvalidos.class);

            usuarios.cambiarPassword(ana.getId(), new CambioPasswordRequest("Cliente123!", "NuevaClave1"));
            assertThat(passwordEncoder.matches("NuevaClave1", usuarioRepo.findById(ana.getId()).orElseThrow().getPasswordHash())).isTrue();
        }
    }

    @Nested
    @DisplayName("Catálogo")
    class Catalogo {

        @Test
        @DisplayName("Un artículo exige stock y un servicio exige duración")
        void segunTipo() {
            Long categoria = consulta.getCategoria().getId();
            ProductoRequest articuloSinStock = new ProductoRequest("Correa", null, 9990, TipoProducto.PRODUCTO, null, null, null, categoria, null);
            assertThatThrownBy(() -> productos.crear(articuloSinStock))
                    .isInstanceOf(ApiException.DatosInvalidos.class)
                    .satisfies(e -> assertThat(((ApiException) e).getErrores()).containsKey("stock"));

            ProductoRequest servicio = new ProductoRequest("Peluquería", null, 15000, TipoProducto.SERVICIO, 50, 40, null, categoria, null);
            var creado = productos.crear(servicio);
            assertThat(creado.stock()).isNull();
            assertThat(creado.duracionMin()).isEqualTo(40);
            assertThat(creado.activo()).isTrue();
        }

        @Test
        @DisplayName("La baja de un producto lo saca del catálogo público pero no lo borra")
        void bajaLogica() {
            productos.darDeBaja(collar.getId());
            assertThat(productos.listar(null, null, false)).noneMatch(p -> p.id().equals(collar.getId()));
            assertThat(productos.listar(null, null, true)).anyMatch(p -> p.id().equals(collar.getId()) && !p.activo());
        }

        @Test
        @DisplayName("Una categoría con productos no se elimina")
        void categoriaConProductos() {
            assertThatThrownBy(() -> categorias.eliminar(consulta.getCategoria().getId())).isInstanceOf(ApiException.Conflicto.class);
        }
    }
}
