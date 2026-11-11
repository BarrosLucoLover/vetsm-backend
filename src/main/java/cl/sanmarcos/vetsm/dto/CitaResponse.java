package cl.sanmarcos.vetsm.dto;

import cl.sanmarcos.vetsm.model.Cita;
import cl.sanmarcos.vetsm.model.EstadoCita;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record CitaResponse(
        Long id,
        Long mascotaId,
        String nombreMascota,
        String nombreDueno,
        String telefonoContacto,
        Long servicioId,
        String servicioNombre,
        LocalDate fecha,
        @Schema(type = "string", example = "10:30") @JsonFormat(pattern = "HH:mm") LocalTime hora,
        EstadoCita estado,
        String notas,
        LocalDateTime fechaCreacion) {

    public static CitaResponse desde(Cita c) {
        return new CitaResponse(c.getId(), c.getMascota() != null ? c.getMascota().getId() : null, c.getNombreMascota(),
                c.getNombreDueno(), c.getTelefonoContacto(), c.getServicio().getId(), c.getServicio().getNombre(),
                c.getFecha(), c.getHora(), c.getEstado(), c.getNotas(), c.getFechaCreacion());
    }
}
