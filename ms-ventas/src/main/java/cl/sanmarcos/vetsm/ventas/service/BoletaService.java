package cl.sanmarcos.vetsm.ventas.service;

import cl.sanmarcos.vetsm.comun.exception.ApiException;
import cl.sanmarcos.vetsm.comun.security.Actor;
import cl.sanmarcos.vetsm.comun.security.Rol;
import cl.sanmarcos.vetsm.ventas.cliente.CatalogoCliente;
import cl.sanmarcos.vetsm.ventas.dto.BoletaResponse;
import cl.sanmarcos.vetsm.ventas.dto.CompraRequest;
import cl.sanmarcos.vetsm.ventas.model.Boleta;
import cl.sanmarcos.vetsm.ventas.model.EstadoBoleta;
import cl.sanmarcos.vetsm.ventas.repository.BoletaRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compras de la tienda. Reglas:
 * - Solo compra un cliente, a su nombre (el cliente sale del token, no del JSON).
 * - Los precios los entrega ms-catalogo desde su base de datos, nunca vienen del navegador.
 * - No se vende más de lo que hay en stock ni productos dados de baja (lo controla ms-catalogo).
 *
 * Como el stock está en otro microservicio, la compra no puede ser una sola transacción de base de
 * datos. Se usa una SAGA con compensación:
 *   0. Validar la tarjeta (sin cobrar todavía).
 *   1. Reservar el stock en ms-catalogo.
 *   2. Cobrar (pasarela simulada) y guardar la boleta en la base de ms-ventas.
 *   3. Si el paso 2 falla, se compensa: se le pide a ms-catalogo devolver el stock reservado.
 */
@Service
@Transactional
public class BoletaService {

    private static final Logger log = LoggerFactory.getLogger(BoletaService.class);

    private final BoletaRepository boletas;
    private final CatalogoCliente catalogo;
    private final PagoService pagos;
    private final TarjetaService tarjetas;

    public BoletaService(BoletaRepository boletas, CatalogoCliente catalogo, PagoService pagos, TarjetaService tarjetas) {
        this.boletas = boletas;
        this.catalogo = catalogo;
        this.pagos = pagos;
        this.tarjetas = tarjetas;
    }

    @Transactional(readOnly = true)
    public List<BoletaResponse> listar() {
        return boletas.findAllByOrderByFechaDesc().stream().map(BoletaResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public List<BoletaResponse> listarDeCliente(Long clienteId) {
        return boletas.findByClienteIdOrderByFechaDesc(clienteId).stream().map(BoletaResponse::desde).toList();
    }

    /**
     * El administrador ve cualquier boleta; un cliente, solo las suyas. Si un cliente pide una boleta
     * ajena se responde 404 (no 403) para no revelar que ese número de boleta existe.
     */
    @Transactional(readOnly = true)
    public BoletaResponse obtener(Long id, Actor actor) {
        Boleta boleta = buscar(id);
        if (!actor.esAdmin() && !boleta.getClienteId().equals(actor.id())) {
            throw new ApiException.NoEncontrado("No existe la boleta " + id + ".");
        }
        return BoletaResponse.desde(boleta);
    }

    /** cliente: quien inició sesión (del token JWT). Nadie puede comprar a nombre de otra persona. */
    public BoletaResponse comprar(Actor cliente, CompraRequest compra) {
        if (cliente.rol() != Rol.CLIENTE) {
            throw new ApiException.ReglaNegocio("Las compras se realizan con una cuenta de cliente.");
        }
        List<CatalogoCliente.Item> pedido = compra.items().stream()
                .map(i -> new CatalogoCliente.Item(i.productoId(), i.cantidad()))
                .toList();

        // 0. Revisa la tarjeta antes de tocar el stock (un error de tipeo no debe reservar nada).
        pagos.validar(cliente.id(), compra.pago());

        // 1. Reserva el stock en ms-catalogo. Si falta stock o un producto no existe, falla aquí y no se cobra nada.
        CatalogoCliente.Reserva reserva = catalogo.reservar(pedido);

        try {
            // 2. Arma la boleta con los nombres y precios que entregó ms-catalogo, cobra y la guarda.
            Boleta boleta = new Boleta(cliente.id(), cliente.nombre(), cliente.email());
            reserva.items().forEach(i -> boleta.agregarDetalle(i.productoId(), i.nombre(), i.precio(), i.cantidad()));

            PagoService.ResultadoPago pago = pagos.cobrar(cliente.id(), compra.pago(), boleta.getTotal());
            boleta.setPagoMarca(pago.marca());
            boleta.setPagoUltimos4(pago.ultimos4());
            boleta.setCodigoAutorizacion(pago.codigoAutorizacion());
            Boleta guardada = boletas.saveAndFlush(boleta);

            if (compra.pago().tarjeta() != null && compra.pago().quiereGuardarTarjeta()) {
                tarjetas.guardar(cliente.id(), compra.pago().tarjeta());
            }
            return BoletaResponse.desde(guardada);
        } catch (RuntimeException error) {
            // 3. Compensación: el stock reservado vuelve a ms-catalogo.
            compensar(pedido, error);
            throw error;
        }
    }

    /** Anula una compra y le pide a ms-catalogo devolver al stock los artículos vendidos. */
    public BoletaResponse anular(Long id) {
        Boleta boleta = buscar(id);
        if (boleta.getEstado() != EstadoBoleta.PAGADA) {
            throw new ApiException.Conflicto("La boleta " + id + " ya está anulada.");
        }
        catalogo.devolver(boleta.getDetalles().stream()
                .map(d -> new CatalogoCliente.Item(d.getProductoId(), d.getCantidad()))
                .toList());
        boleta.setEstado(EstadoBoleta.ANULADA);
        return BoletaResponse.desde(boleta);
    }

    /** Cuántas compras tiene un cliente (lo pregunta ms-usuarios antes de eliminar una cuenta). */
    @Transactional(readOnly = true)
    public long contarDeCliente(Long clienteId) {
        return boletas.countByClienteId(clienteId);
    }

    private void compensar(List<CatalogoCliente.Item> pedido, RuntimeException motivo) {
        try {
            catalogo.devolver(pedido);
            log.info("Compra cancelada ({}): se devolvió el stock reservado.", motivo.getMessage());
        } catch (RuntimeException e) {
            // Si ni siquiera se puede devolver, queda registrado para corregirlo a mano.
            log.error("No se pudo devolver el stock reservado {}: {}", pedido, e.getMessage());
        }
    }

    private Boleta buscar(Long id) {
        return boletas.findById(id).orElseThrow(() -> new ApiException.NoEncontrado("No existe la boleta " + id + "."));
    }
}
