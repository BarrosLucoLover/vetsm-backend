package cl.sanmarcos.vetsm.controller;

import cl.sanmarcos.vetsm.dto.ResumenResponse;
import cl.sanmarcos.vetsm.service.ReporteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reportes")
@Tag(name = "Reportes")
public class ReporteController {

    private final ReporteService servicio;

    public ReporteController(ReporteService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/resumen")
    @Operation(summary = "Métricas del panel de administración",
            description = "Rol: ADMIN. Citas por estado y por servicio, citas de hoy, ventas, productos más vendidos y stock bajo.")
    public ResumenResponse resumen() {
        return servicio.resumen();
    }
}
