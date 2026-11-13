package cl.sanmarcos.vetsm.agenda.controller;

import cl.sanmarcos.vetsm.agenda.cliente.CatalogoCliente;
import cl.sanmarcos.vetsm.agenda.model.Cita;
import cl.sanmarcos.vetsm.agenda.model.EstadoCita;
import cl.sanmarcos.vetsm.agenda.repository.CitaRepository;
import cl.sanmarcos.vetsm.comun.exception.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Métricas de la agenda para el panel de administración. */
@RestController
@RequestMapping("/api/v1/reportes/citas")
@Tag(name = "Reportes")
@PreAuthorize("hasRole('ADMIN')")
public class ReporteCitasController {

    public record Conteo(String etiqueta, long valor) {
    }

    public record ReporteCitas(Map<String, Long> citasPorEstado, long citasDeHoy, List<Conteo> citasPorServicio) {
    }

    private final CitaRepository citas;
    private final CatalogoCliente catalogo;
    private final Clock reloj;

    public ReporteCitasController(CitaRepository citas, CatalogoCliente catalogo, Clock reloj) {
        this.citas = citas;
        this.catalogo = catalogo;
        this.reloj = reloj;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @Operation(summary = "Resumen de la agenda", description = "Rol: ADMIN. Citas por estado, citas de hoy y citas por servicio.")
    public ReporteCitas resumen() {
        List<Cita> todas = citas.findAll();
        LocalDate hoy = LocalDate.now(reloj);

        Map<String, Long> porEstado = new LinkedHashMap<>();
        Arrays.stream(EstadoCita.values()).forEach(e -> porEstado.put(e.name(), 0L));
        todas.forEach(c -> porEstado.merge(c.getEstado().name(), 1L, Long::sum));
        long deHoy = todas.stream().filter(c -> c.getFecha().equals(hoy) && c.getEstado() != EstadoCita.CANCELADA).count();

        Map<String, Long> conteo = todas.stream()
                .filter(c -> c.getEstado() != EstadoCita.CANCELADA)
                .collect(Collectors.groupingBy(Cita::getServicioNombre, Collectors.counting()));
        return new ReporteCitas(porEstado, deHoy, porServicio(conteo));
    }

    /**
     * Muestra todos los servicios del catálogo (también los que no tienen citas). Si ms-catalogo no
     * responde, el reporte igual sale, solo con los servicios que tienen citas.
     */
    private List<Conteo> porServicio(Map<String, Long> conteo) {
        try {
            return catalogo.serviciosActivos().stream()
                    .map(s -> new Conteo(s.nombre(), conteo.getOrDefault(s.nombre(), 0L)))
                    .toList();
        } catch (ApiException e) {
            return conteo.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entrada -> new Conteo(entrada.getKey(), entrada.getValue()))
                    .toList();
        }
    }
}
