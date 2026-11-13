package cl.sanmarcos.vetsm.catalogo.controller;

import cl.sanmarcos.vetsm.catalogo.dto.ProductoResponse;
import cl.sanmarcos.vetsm.catalogo.service.InventarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Métricas de inventario para el panel de administración. */
@RestController
@RequestMapping("/api/v1/reportes/inventario")
@Tag(name = "Reportes")
@PreAuthorize("hasRole('ADMIN')")
public class ReporteInventarioController {

    public record ReporteInventario(List<ProductoResponse> productosConStockBajo) {
    }

    private final InventarioService inventario;

    public ReporteInventarioController(InventarioService inventario) {
        this.inventario = inventario;
    }

    @GetMapping
    @Operation(summary = "Artículos con stock bajo", description = "Rol: ADMIN. Artículos a la venta con 5 unidades o menos.")
    public ReporteInventario resumen() {
        return new ReporteInventario(inventario.conStockBajo());
    }
}
