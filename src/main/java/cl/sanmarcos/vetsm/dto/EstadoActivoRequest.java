package cl.sanmarcos.vetsm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record EstadoActivoRequest(
        @Schema(example = "false")
        @NotNull(message = "Indica si la cuenta queda activa (true) o inactiva (false).")
        Boolean activo) {
}
