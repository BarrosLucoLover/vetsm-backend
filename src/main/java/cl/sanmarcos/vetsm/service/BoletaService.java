package cl.sanmarcos.vetsm.service;

import cl.sanmarcos.vetsm.dto.BoletaResponse;
import cl.sanmarcos.vetsm.dto.CompraRequest;
import cl.sanmarcos.vetsm.dto.ItemCompraRequest;
import cl.sanmarcos.vetsm.exception.ApiException;
import cl.sanmarcos.vetsm.model.Boleta;
import cl.sanmarcos.vetsm.model.DetalleBoleta;
import cl.sanmarcos.vetsm.model.EstadoBoleta;
import cl.sanmarcos.vetsm.model.Producto;
import cl.sanmarcos.vetsm.model.Rol;
import cl.sanmarcos.vetsm.model.Usuario;
import cl.sanmarcos.vetsm.repository.BoletaRepository;
import cl.sanmarcos.vetsm.security.Actor;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compras de la tienda. Reglas:
 * - Solo compra un cliente con su cuenta activa.
 * - Los precios se toman de la base de datos, nunca del navegador (no se pueden manipular).
 * - No se vende más de lo que hay en stock ni productos dados de baja.
 * - Todo ocurre en una transacción: si algo falla, no se descuenta stock ni queda una boleta a medias.
 */
@Service
@Transactional
public class BoletaService {

    private final BoletaRepository boletas;
    private final UsuarioService usuarios;
    private final ProductoService productos;
    private final PagoService pagos;
    private final TarjetaService tarjetas;

    public BoletaService(BoletaRepository boletas, UsuarioService usuarios, ProductoService productos, PagoService pagos,
            TarjetaService tarjetas) {
        this.boletas = boletas;
        this.usuarios = usuarios;
        this.productos = productos;
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
        if (!actor.esAdmin() && !boleta.getCliente().getId().equals(actor.id())) {
            throw new ApiException.NoEncontrado("No existe la boleta " + id + ".");
        }
        return BoletaResponse.desde(boleta);
    }

    /** clienteId viene del token JWT: nadie puede comprar a nombre de otra persona. */
    public BoletaResponse comprar(Long clienteId, CompraRequest compra) {
        Usuario cliente = usuarios.buscar(clienteId);
        if (cliente.getRol() != Rol.CLIENTE) {
            throw new ApiException.ReglaNegocio("Las compras se realizan con una cuenta de cliente.");
        }
        if (!cliente.isActivo()) {
            throw new ApiException.ReglaNegocio("La cuenta de " + cliente.getNombre() + " está desactivada.");
        }

        // 1. Arma la boleta con los precios y el stock actuales (si el mismo producto viene dos veces, se suma).
        Map<Long, Integer> cantidades = new LinkedHashMap<>();
        for (ItemCompraRequest item : compra.items()) {
            cantidades.merge(item.productoId(), item.cantidad(), Integer::sum);
        }
        Boleta boleta = new Boleta(cliente);
        cantidades.forEach((productoId, cantidad) -> {
            Producto producto = productos.buscar(productoId);
            if (!producto.isActivo()) {
                throw new ApiException.ReglaNegocio("\"" + producto.getNombre() + "\" ya no está disponible.");
            }
            if (!producto.hayStockPara(cantidad)) {
                throw new ApiException.Conflicto("Stock insuficiente de \"" + producto.getNombre() + "\": quedan "
                        + producto.getStock() + " y pediste " + cantidad + ".");
            }
            boleta.agregarDetalle(producto, cantidad);
        });

        // 2. Cobra. Si la pasarela rechaza, se lanza una excepción y no se guarda nada.
        PagoService.ResultadoPago pago = pagos.cobrar(cliente, compra.pago(), boleta.getTotal());

        // 3. Descuenta el stock de los artículos y guarda la boleta con los datos seguros del pago.
        for (DetalleBoleta detalle : boleta.getDetalles()) {
            Producto producto = detalle.getProducto();
            if (!producto.esServicio()) {
                producto.setStock(producto.getStock() - detalle.getCantidad());
            }
        }
        boleta.setPagoMarca(pago.marca());
        boleta.setPagoUltimos4(pago.ultimos4());
        boleta.setCodigoAutorizacion(pago.codigoAutorizacion());
        Boleta guardada = boletas.save(boleta);

        if (compra.pago().tarjeta() != null && compra.pago().quiereGuardarTarjeta()) {
            tarjetas.guardar(cliente, compra.pago().tarjeta());
        }
        return BoletaResponse.desde(guardada);
    }

    /** Anula una compra y devuelve al stock los artículos vendidos. */
    public BoletaResponse anular(Long id) {
        Boleta boleta = buscar(id);
        if (boleta.getEstado() != EstadoBoleta.PAGADA) {
            throw new ApiException.Conflicto("La boleta " + id + " ya está anulada.");
        }
        for (DetalleBoleta detalle : boleta.getDetalles()) {
            Producto producto = detalle.getProducto();
            if (!producto.esServicio()) {
                producto.setStock(producto.getStock() + detalle.getCantidad());
            }
        }
        boleta.setEstado(EstadoBoleta.ANULADA);
        return BoletaResponse.desde(boleta);
    }

    private Boleta buscar(Long id) {
        return boletas.findById(id).orElseThrow(() -> new ApiException.NoEncontrado("No existe la boleta " + id + "."));
    }
}
