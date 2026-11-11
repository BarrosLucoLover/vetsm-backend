package cl.sanmarcos.vetsm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

@Schema(description = """
        Datos de una cita. Hay dos formas de indicar el paciente:
        - mascotaId: una mascota registrada (los datos de contacto se toman de su dueño).
        - Sin mascotaId: nombreMascota, nombreDueno y telefonoContacto son obligatorios (solicitud sin cuenta).""")
public record CitaRequest(
        @Schema(example = "1")
        Long mascotaId,

        @Schema(example = "Luna")
        @Size(max = 60, message = "El nombre de la mascota puede tener hasta 60 caracteres.")
        String nombreMascota,

        @Schema(example = "Pedro Soto")
        @Size(max = 100, message = "El nombre del dueño puede tener hasta 100 caracteres.")
        String nombreDueno,

        @Schema(example = "+56 9 1122 3344")
        @Pattern(regexp = Validaciones.CELULAR, message = Validaciones.MENSAJE_CELULAR)
        String telefonoContacto,

        @Schema(example = "2", description = "Id de un producto de tipo SERVICIO")
        @NotNull(message = "El servicio es obligatorio.")
        Long servicioId,

        @Schema(example = "2026-12-01", type = "string", format = "date")
        @NotNull(message = "La fecha es obligatoria.")
        LocalDate fecha,

        @Schema(example = "10:30", type = "string", pattern = "HH:mm")
        @NotNull(message = "La hora es obligatoria.")
        LocalTime hora,

        @Schema(example = "Refuerzo de vacuna séxtuple.")
        @Size(max = 500, message = "Las notas pueden tener hasta 500 caracteres.")
        String notas) {
}
