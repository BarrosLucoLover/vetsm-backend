package cl.sanmarcos.vetsm.ventas.service;

import static org.assertj.core.api.Assertions.assertThat;

import cl.sanmarcos.vetsm.ventas.dto.TarjetaRequest;
import cl.sanmarcos.vetsm.ventas.model.MarcaTarjeta;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Mismos casos que src/utils/tarjeta.test.js del frontend: ambos lados validan igual. */
class ValidadorTarjetaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 11);
    private final TarjetaRequest valida = new TarjetaRequest("4111 1111 1111 1111", "Ana García", "12/30");

    @Test
    @DisplayName("Reconoce Visa, Mastercard (series 5 y 2), American Express y Diners")
    void detectaMarcas() {
        assertThat(MarcaTarjeta.detectar("4111111111111111")).isEqualTo(MarcaTarjeta.VISA);
        assertThat(MarcaTarjeta.detectar("5555555555554444")).isEqualTo(MarcaTarjeta.MASTERCARD);
        assertThat(MarcaTarjeta.detectar("2221000000000009")).isEqualTo(MarcaTarjeta.MASTERCARD);
        assertThat(MarcaTarjeta.detectar("378282246310005")).isEqualTo(MarcaTarjeta.AMEX);
        assertThat(MarcaTarjeta.detectar("36227206271667")).isEqualTo(MarcaTarjeta.DINERS);
        assertThat(MarcaTarjeta.detectar("9999")).isNull();
    }

    @Test
    @DisplayName("El algoritmo de Luhn detecta un dígito mal escrito")
    void luhn() {
        assertThat(ValidadorTarjeta.pasaLuhn("4111111111111111")).isTrue();
        assertThat(ValidadorTarjeta.pasaLuhn("4111111111111112")).isFalse();
        assertThat(ValidadorTarjeta.pasaLuhn("4111")).isFalse();
    }

    @Test
    @DisplayName("Lee el vencimiento MM/AA y rechaza meses inválidos")
    void vencimiento() {
        assertThat(ValidadorTarjeta.leerVencimiento("08/29")).isEqualTo(YearMonth.of(2029, 8));
        assertThat(ValidadorTarjeta.leerVencimiento("13/29")).isNull();
        assertThat(ValidadorTarjeta.leerVencimiento("0829")).isNull();
    }

    @Test
    @DisplayName("El CVV tiene 3 dígitos, o 4 en American Express")
    void cvv() {
        assertThat(ValidadorTarjeta.validarCvv("123", MarcaTarjeta.VISA)).isNull();
        assertThat(ValidadorTarjeta.validarCvv("12", MarcaTarjeta.VISA)).contains("3 dígitos");
        assertThat(ValidadorTarjeta.validarCvv("1234", MarcaTarjeta.AMEX)).isNull();
        assertThat(ValidadorTarjeta.validarCvv("", MarcaTarjeta.VISA)).contains("Ingresa");
    }

    @Test
    @DisplayName("Una tarjeta válida no tiene errores")
    void tarjetaValida() {
        assertThat(ValidadorTarjeta.validar(valida, "123", true, HOY)).isEmpty();
        assertThat(ValidadorTarjeta.validar(valida, null, false, HOY)).isEmpty();
    }

    @Test
    @DisplayName("Informa cada campo incorrecto")
    void errores() {
        assertThat(ValidadorTarjeta.validar(new TarjetaRequest("", "A", "1"), "", true, HOY))
                .containsOnlyKeys("tarjeta.numero", "tarjeta.titular", "tarjeta.vencimiento", "cvv");
        assertThat(ValidadorTarjeta.validar(new TarjetaRequest("4111 1111 1111 1112", "Ana", "12/30"), "123", true, HOY))
                .containsEntry("tarjeta.numero", "El número de la tarjeta no es válido. Revisa que esté bien escrito.");
        assertThat(ValidadorTarjeta.validar(new TarjetaRequest("4111 1111 1111 1111", "Ana", "10/26"), "123", true, HOY))
                .containsEntry("tarjeta.vencimiento", "La tarjeta está vencida.");
        assertThat(ValidadorTarjeta.validar(null, "123", true, HOY)).containsKey("tarjeta");
    }
}
