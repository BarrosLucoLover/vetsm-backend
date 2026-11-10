package cl.sanmarcos.vetsm.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pruebas unitarias de las reglas que viven en las propias entidades (no necesitan base de datos). */
class ReglasDelModeloTest {

    private final Categoria categoria = new Categoria("Pruebas", null);

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
    @DisplayName("Un servicio siempre tiene disponibilidad; un artículo depende de su stock")
    void disponibilidadSegunTipo() {
        Producto consulta = Producto.servicio("Consulta", null, 25000, 30, categoria);
        Producto collar = Producto.articulo("Collar", null, 12990, 3, categoria);

        assertThat(consulta.esServicio()).isTrue();
        assertThat(consulta.getStock()).isNull();
        assertThat(consulta.hayStockPara(100)).isTrue();

        assertThat(collar.esServicio()).isFalse();
        assertThat(collar.hayStockPara(3)).isTrue();
        assertThat(collar.hayStockPara(4)).isFalse();
    }

    @Test
    @DisplayName("La boleta calcula subtotales y total, y guarda el precio del momento de la compra")
    void totalDeLaBoleta() {
        Producto consulta = Producto.servicio("Consulta", null, 25000, 30, categoria);
        Producto collar = Producto.articulo("Collar", null, 12990, 10, categoria);
        Boleta boleta = new Boleta(new Usuario("Ana", "ana@correo.cl", "hash", null, Rol.CLIENTE));

        boleta.agregarDetalle(consulta, 1);
        DetalleBoleta lineaCollar = boleta.agregarDetalle(collar, 2);

        assertThat(lineaCollar.getSubtotal()).isEqualTo(25980);
        assertThat(boleta.getTotal()).isEqualTo(50980);
        assertThat(boleta.getDetalles()).hasSize(2);

        collar.setPrecio(99999);
        assertThat(lineaCollar.getPrecioUnitario()).isEqualTo(12990);
        assertThat(boleta.getTotal()).isEqualTo(50980);
    }
}
