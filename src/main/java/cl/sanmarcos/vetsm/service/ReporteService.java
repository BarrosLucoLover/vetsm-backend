package cl.sanmarcos.vetsm.service;

import cl.sanmarcos.vetsm.dto.ProductoResponse;
import cl.sanmarcos.vetsm.dto.ResumenResponse;
import cl.sanmarcos.vetsm.model.Cita;
import cl.sanmarcos.vetsm.model.EstadoBoleta;
import cl.sanmarcos.vetsm.model.EstadoCita;
import cl.sanmarcos.vetsm.model.Rol;
import cl.sanmarcos.vetsm.model.TipoProducto;
import cl.sanmarcos.vetsm.repository.BoletaRepository;
import cl.sanmarcos.vetsm.repository.CitaRepository;
import cl.sanmarcos.vetsm.repository.ProductoRepository;
import cl.sanmarcos.vetsm.repository.UsuarioRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Métricas del panel de administración (RF10: reportes de atención). */
@Service
@Transactional(readOnly = true)
public class ReporteService {

    /** Bajo este número de unidades, un artículo aparece como "stock bajo". */
    static final int UMBRAL_STOCK_BAJO = 5;

    private final CitaRepository citas;
    private final BoletaRepository boletas;
    private final ProductoRepository productos;
    private final UsuarioRepository usuarios;
    private final Clock reloj;

    public ReporteService(CitaRepository citas, BoletaRepository boletas, ProductoRepository productos, UsuarioRepository usuarios,
            Clock reloj) {
        this.citas = citas;
        this.boletas = boletas;
        this.productos = productos;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    public ResumenResponse resumen() {
        List<Cita> todas = citas.findAll();
        LocalDate hoy = LocalDate.now(reloj);

        Map<String, Long> porEstado = new LinkedHashMap<>();
        Arrays.stream(EstadoCita.values()).forEach(e -> porEstado.put(e.name(), 0L));
        todas.forEach(c -> porEstado.merge(c.getEstado().name(), 1L, Long::sum));

        long deHoy = todas.stream().filter(c -> c.getFecha().equals(hoy) && c.getEstado() != EstadoCita.CANCELADA).count();

        Map<String, Long> conteoServicios = todas.stream()
                .filter(c -> c.getEstado() != EstadoCita.CANCELADA)
                .collect(Collectors.groupingBy(c -> c.getServicio().getNombre(), Collectors.counting()));
        List<ResumenResponse.Conteo> porServicio = productos.findByTipoAndActivoTrueOrderByNombreAsc(TipoProducto.SERVICIO).stream()
                .map(s -> new ResumenResponse.Conteo(s.getNombre(), conteoServicios.getOrDefault(s.getNombre(), 0L)))
                .toList();

        List<ResumenResponse.VentaProducto> ventas = boletas.ventasPorProducto(EstadoBoleta.PAGADA).stream()
                .map(v -> new ResumenResponse.VentaProducto(v.getProductoId(), v.getNombre(), v.getUnidades(), v.getMonto()))
                .toList();

        List<ProductoResponse> stockBajo = productos.findByTipoAndActivoTrueOrderByNombreAsc(TipoProducto.PRODUCTO).stream()
                .filter(p -> p.getStock() != null && p.getStock() <= UMBRAL_STOCK_BAJO)
                .map(ProductoResponse::desde)
                .toList();

        return new ResumenResponse(porEstado, deHoy, porServicio, boletas.countByEstado(EstadoBoleta.PAGADA),
                boletas.sumarTotalPorEstado(EstadoBoleta.PAGADA), ventas, usuarios.countByRolAndActivoTrue(Rol.CLIENTE), stockBajo);
    }
}
