package cl.sanmarcos.vetsm.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Datos de una tarjeta nueva. El número completo solo se usa para validar y pagar en el momento:
 * nunca se guarda. La validación detallada (marca, Luhn, vencimiento) la hace ValidadorTarjeta.
 */
@Schema(description = "Tarjeta nueva. El número completo nunca se guarda.")
public record TarjetaRequest(
        @Schema(example = "4111 1111 1111 1111", description = "Número de prueba. Se aceptan espacios o guiones")
        String numero,

        @Schema(example = "Ana García")
        String titular,

        @Schema(example = "12/30", description = "Formato MM/AA")
        String vencimiento) {
}
