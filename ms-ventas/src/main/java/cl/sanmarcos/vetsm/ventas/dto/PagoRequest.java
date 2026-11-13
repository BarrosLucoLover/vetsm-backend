package cl.sanmarcos.vetsm.ventas.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@Schema(description = """
        Medio de pago. Se usa UNA de estas opciones:
        - tarjetaGuardadaId: una tarjeta guardada del cliente.
        - tarjeta: una tarjeta nueva (y guardarTarjeta=true para dejarla en la cuenta).
        El cvv se pide siempre y nunca se guarda.""")
public record PagoRequest(
        Long tarjetaGuardadaId,

        @Valid
        TarjetaRequest tarjeta,

        @Schema(example = "123")
        @NotBlank(message = "Ingresa el código de seguridad.")
        String cvv,

        @Schema(example = "true", description = "Opcional; por defecto false")
        Boolean guardarTarjeta) {

    /** Jackson 3 no acepta un boolean primitivo ausente en el JSON: el campo es Boolean y aquí se resuelve. */
    public boolean quiereGuardarTarjeta() {
        return Boolean.TRUE.equals(guardarTarjeta);
    }
}
