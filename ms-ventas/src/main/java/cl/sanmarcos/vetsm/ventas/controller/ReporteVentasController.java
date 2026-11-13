package cl.sanmarcos.vetsm.ventas.controller;

import cl.sanmarcos.vetsm.ventas.model.EstadoBoleta;
import cl.sanmarcos.vetsm.ventas.repository.BoletaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Métricas de ventas para el panel de administración. */
@RestController
@RequestMapping("/api/v1/reportes/ventas")
@Tag(name = "Reportes")
@PreAuthorize("hasRole('ADMIN')")
public class ReporteVentasController {

    public record VentaProducto(Long productoId, String nombre, long unidades, long monto) {
    }

    public record ReporteVentas(long boletasPagadas, long ventasTotales, List<VentaProducto> ventasPorProducto) {
    }

    private final BoletaRepository boletas;

    public ReporteVentasController(BoletaRepository boletas) {
        this.boletas = boletas;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Ventas de la tienda", description = "Rol: ADMIN. Boletas pagadas, monto total y lo más vendido.")
    public ReporteVentas resumen() {
        List<VentaProducto> porProducto = boletas.ventasPorProducto(EstadoBoleta.PAGADA).stream()
                .map(v -> new VentaProducto(v.getProductoId(), v.getNombre(), v.getUnidades(), v.getMonto()))
                .toList();
        return new ReporteVentas(boletas.countByEstado(EstadoBoleta.PAGADA), boletas.sumarTotalPorEstado(EstadoBoleta.PAGADA), porProducto);
    }
}
