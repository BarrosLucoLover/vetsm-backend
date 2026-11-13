package cl.sanmarcos.vetsm.agenda.dto;

import cl.sanmarcos.vetsm.agenda.model.EstadoCita;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CambioEstadoCitaRequest(
        @Schema(example = "CONFIRMADA")
        @NotNull(message = "El nuevo estado es obligatorio.")
        EstadoCita estado) {
}
