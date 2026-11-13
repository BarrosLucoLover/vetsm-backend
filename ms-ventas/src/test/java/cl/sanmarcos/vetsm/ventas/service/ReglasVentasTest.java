package cl.sanmarcos.vetsm.ventas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.sanmarcos.vetsm.comun.exception.ApiException;
import cl.sanmarcos.vetsm.comun.security.Actor;
import cl.sanmarcos.vetsm.comun.security.Rol;
import cl.sanmarcos.vetsm.ventas.cliente.CatalogoCliente;
import cl.sanmarcos.vetsm.ventas.dto.BoletaResponse;
import cl.sanmarcos.vetsm.ventas.dto.CompraRequest;
import cl.sanmarcos.vetsm.ventas.dto.ItemCompraRequest;
import cl.sanmarcos.vetsm.ventas.dto.PagoRequest;
import cl.sanmarcos.vetsm.ventas.dto.TarjetaRequest;
import cl.sanmarcos.vetsm.ventas.model.EstadoBoleta;
import cl.sanmarcos.vetsm.ventas.model.MarcaTarjeta;
import cl.sanmarcos.vetsm.ventas.repository.BoletaRepository;
import cl.sanmarcos.vetsm.ventas.repository.TarjetaRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reglas de compra y pago de ms-ventas. ms-catalogo se simula (mock): así se prueba qué le pide
 * ms-ventas (reservar y, si algo falla, devolver el stock) sin tener que levantarlo.
 */
@SpringBootTest
@Transactional
class ReglasVentasTest {

    private static final Actor ANA = new Actor(3L, Rol.CLIENTE, "Ana García", "ana.garcia@correo.cl");
    private static final Actor PEDRO = new Actor(50L, Rol.CLIENTE, "Pedro Soto", "pedro@correo.cl");
    private static final TarjetaRequest VISA = new TarjetaRequest("4111 1111 1111 1111", "Ana García", "12/35");
    private static final TarjetaRequest RECHAZADA = new TarjetaRequest("4000 0000 0000 0002", "Ana García", "12/35");

    @Autowired private BoletaService boletas;
    @Autowired private TarjetaService tarjetas;
    @Autowired private BoletaRepository boletaRepo;
    @Autowired private TarjetaRepository tarjetaRepo;

    @MockitoBean private CatalogoCliente catalogo;

    /** Lo que respondería ms-catalogo al reservar: nombre y precio de su base de datos. */
    @BeforeEach
    void catalogoSimulado() {
        when(catalogo.reservar(anyList())).thenReturn(new CatalogoCliente.Reserva(List.of(
                new CatalogoCliente.ItemReservado(9L, "Collar antipulgas", "PRODUCTO", 12990, 2),
                new CatalogoCliente.ItemReservado(1L, "Consulta General", "SERVICIO", 25000, 1))));
    }

    private static CompraRequest compra(PagoRequest pago) {
        return new CompraRequest(List.of(new ItemCompraRequest(9L, 2), new ItemCompraRequest(1L, 1)), pago);
    }

    private static PagoRequest pagoNuevo(TarjetaRequest tarjeta, boolean guardar) {
        return new PagoRequest(null, tarjeta, "123", guardar);
    }

    @Test
    @DisplayName("Una compra aprobada reserva el stock en ms-catalogo y crea la boleta con sus precios")
    void compraAprobada() {
        BoletaResponse boleta = boletas.comprar(ANA, compra(pagoNuevo(VISA, false)));

        assertThat(boleta.total()).isEqualTo(2 * 12990 + 25000);
        assertThat(boleta.estado()).isEqualTo(EstadoBoleta.PAGADA);
        assertThat(boleta.clienteNombre()).isEqualTo("Ana García");
        assertThat(boleta.detalles()).extracting(BoletaResponse.Detalle::nombre).containsExactly("Collar antipulgas", "Consulta General");
        assertThat(boleta.medioPago().marca()).isEqualTo(MarcaTarjeta.VISA);
        assertThat(boleta.medioPago().ultimos4()).isEqualTo("1111");
        assertThat(boleta.medioPago().codigoAutorizacion()).matches("\\d{6}");
        verify(catalogo).reservar(List.of(new CatalogoCliente.Item(9L, 2), new CatalogoCliente.Item(1L, 1)));
        verify(catalogo, never()).devolver(anyList());
    }

    @Test
    @DisplayName("Si el banco rechaza la tarjeta, se compensa: se devuelve el stock y no queda boleta")
    void tarjetaRechazadaCompensa() {
        assertThatThrownBy(() -> boletas.comprar(ANA, compra(pagoNuevo(RECHAZADA, false))))
                .isInstanceOf(ApiException.PagoRechazado.class);
        verify(catalogo).devolver(List.of(new CatalogoCliente.Item(9L, 2), new CatalogoCliente.Item(1L, 1)));
        assertThat(boletaRepo.count()).isZero();
    }

    @Test
    @DisplayName("Una tarjeta mal escrita se rechaza antes de reservar stock")
    void tarjetaInvalidaNoReserva() {
        TarjetaRequest malEscrita = new TarjetaRequest("4111 1111 1111 1112", "Ana", "12/35");
        assertThatThrownBy(() -> boletas.comprar(ANA, compra(pagoNuevo(malEscrita, false))))
                .isInstanceOf(ApiException.DatosInvalidos.class)
                .satisfies(e -> assertThat(((ApiException) e).getErrores()).containsKey("tarjeta.numero"));
        verify(catalogo, never()).reservar(anyList());
    }

    @Test
    @DisplayName("Si ms-catalogo responde que falta stock, el error llega tal cual y no se cobra")
    void sinStock() {
        when(catalogo.reservar(anyList())).thenThrow(new ApiException.DeOtroServicio(409, "Stock insuficiente de \"Collar antipulgas\".", null));
        assertThatThrownBy(() -> boletas.comprar(ANA, compra(pagoNuevo(VISA, false))))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Stock insuficiente")
                .satisfies(e -> assertThat(((ApiException) e).getStatus()).isEqualTo(409));
        assertThat(boletaRepo.count()).isZero();
    }

    @Test
    @DisplayName("Si ms-catalogo no responde, la compra no se hace (503)")
    void catalogoCaido() {
        when(catalogo.reservar(anyList())).thenThrow(new ApiException.ServicioNoDisponible("catálogo"));
        assertThatThrownBy(() -> boletas.comprar(ANA, compra(pagoNuevo(VISA, false))))
                .isInstanceOf(ApiException.ServicioNoDisponible.class);
    }

    @Test
    @DisplayName("Al guardar la tarjeta no se almacena el número completo ni el CVV")
    void guardaSoloDatosSeguros() {
        boletas.comprar(ANA, compra(pagoNuevo(VISA, true)));
        var guardada = tarjetaRepo.findByUsuarioIdOrderByPredeterminadaDescIdDesc(ANA.id()).get(0);
        assertThat(guardada.getUltimos4()).isEqualTo("1111");
        assertThat(guardada.isPredeterminada()).isTrue();
        assertThat(guardada.toString()).doesNotContain("4111111111111111");
    }

    @Test
    @DisplayName("Con una tarjeta guardada solo se pide el CVV; no se puede usar la tarjeta de otro cliente")
    void tarjetaGuardada() {
        Long id = tarjetas.guardar(ANA.id(), VISA).tarjeta().id();
        BoletaResponse boleta = boletas.comprar(ANA, compra(new PagoRequest(id, null, "456", false)));
        assertThat(boleta.medioPago().ultimos4()).isEqualTo("1111");

        assertThatThrownBy(() -> boletas.comprar(PEDRO, compra(new PagoRequest(id, null, "456", false))))
                .isInstanceOf(ApiException.NoEncontrado.class);
    }

    @Test
    @DisplayName("Hay que elegir una tarjeta guardada o una nueva")
    void unSoloMedioDePago() {
        assertThatThrownBy(() -> boletas.comprar(ANA, compra(new PagoRequest(null, null, "123", false))))
                .isInstanceOf(ApiException.DatosInvalidos.class);
    }

    @Test
    @DisplayName("El personal de la clínica no compra con su cuenta")
    void personalNoCompra() {
        Actor admin = new Actor(1L, Rol.ADMIN, "Administrador General", "admin@sanmarcos.cl");
        assertThatThrownBy(() -> boletas.comprar(admin, compra(pagoNuevo(VISA, false)))).isInstanceOf(ApiException.ReglaNegocio.class);
        verify(catalogo, never()).reservar(anyList());
    }

    @Test
    @DisplayName("Anular una compra le pide a ms-catalogo devolver el stock y no se puede anular dos veces")
    void anular() {
        Long id = boletas.comprar(ANA, compra(pagoNuevo(VISA, false))).id();
        assertThat(boletas.anular(id).estado()).isEqualTo(EstadoBoleta.ANULADA);
        verify(catalogo).devolver(List.of(new CatalogoCliente.Item(9L, 2), new CatalogoCliente.Item(1L, 1)));
        assertThatThrownBy(() -> boletas.anular(id)).isInstanceOf(ApiException.Conflicto.class);
    }

    @Test
    @DisplayName("Cada cliente ve solo sus boletas; una boleta ajena responde como inexistente")
    void boletasPropias() {
        Long id = boletas.comprar(ANA, compra(pagoNuevo(VISA, false))).id();
        assertThat(boletas.listarDeCliente(ANA.id())).hasSize(1);
        assertThat(boletas.listarDeCliente(PEDRO.id())).isEmpty();
        assertThat(boletas.contarDeCliente(ANA.id())).isEqualTo(1);
        assertThatThrownBy(() -> boletas.obtener(id, PEDRO)).isInstanceOf(ApiException.NoEncontrado.class);
    }
}
