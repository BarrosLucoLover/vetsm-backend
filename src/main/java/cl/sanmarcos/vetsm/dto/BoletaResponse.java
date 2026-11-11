package cl.sanmarcos.vetsm.dto;

import cl.sanmarcos.vetsm.model.Boleta;
import cl.sanmarcos.vetsm.model.DetalleBoleta;
import cl.sanmarcos.vetsm.model.EstadoBoleta;
import cl.sanmarcos.vetsm.model.MarcaTarjeta;
import java.time.LocalDateTime;
import java.util.List;

public record BoletaResponse(
        Long id,
        LocalDateTime fecha,
        Long clienteId,
        String clienteNombre,
        String clienteEmail,
        EstadoBoleta estado,
        Integer total,
        MedioPago medioPago,
        List<Detalle> detalles) {

    /** Solo datos seguros del pago. */
    public record MedioPago(MarcaTarjeta marca, String marcaNombre, String ultimos4, String codigoAutorizacion) {
    }

    public record Detalle(Long productoId, String nombre, Integer cantidad, Integer precioUnitario, Integer subtotal) {

        static Detalle desde(DetalleBoleta d) {
            return new Detalle(d.getProducto().getId(), d.getProducto().getNombre(), d.getCantidad(), d.getPrecioUnitario(), d.getSubtotal());
        }
    }

    public static BoletaResponse desde(Boleta b) {
        MedioPago pago = b.getPagoMarca() == null ? null
                : new MedioPago(b.getPagoMarca(), b.getPagoMarca().getNombre(), b.getPagoUltimos4(), b.getCodigoAutorizacion());
        return new BoletaResponse(b.getId(), b.getFecha(), b.getCliente().getId(), b.getCliente().getNombre(), b.getCliente().getEmail(),
                b.getEstado(), b.getTotal(), pago, b.getDetalles().stream().map(Detalle::desde).toList());
    }
}
