package cl.sanmarcos.vetsm.ventas.service;

import cl.sanmarcos.vetsm.ventas.dto.TarjetaRequest;
import cl.sanmarcos.vetsm.ventas.model.MarcaTarjeta;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validación de tarjetas. Hace lo mismo que src/utils/tarjeta.js del frontend: el servidor nunca
 * confía en que el navegador ya validó.
 */
public final class ValidadorTarjeta {

    private static final Pattern VENCIMIENTO = Pattern.compile("^(\\d{2})/(\\d{2})$");
    private static final Pattern TITULAR = Pattern.compile("^[\\p{L} .'-]{3,60}$");

    private ValidadorTarjeta() {
    }

    public static String soloDigitos(String valor) {
        return valor == null ? "" : valor.replaceAll("\\D", "");
    }

    /** Algoritmo de Luhn: detecta números mal escritos. */
    public static boolean pasaLuhn(String digitos) {
        if (digitos.length() < 12) {
            return false;
        }
        int suma = 0;
        for (int i = 0; i < digitos.length(); i++) {
            int digito = digitos.charAt(digitos.length() - 1 - i) - '0';
            if (i % 2 == 1) {
                digito *= 2;
                if (digito > 9) {
                    digito -= 9;
                }
            }
            suma += digito;
        }
        return suma % 10 == 0;
    }

    /** Convierte "MM/AA" en un YearMonth, o null si no es una fecha válida. */
    public static YearMonth leerVencimiento(String texto) {
        Matcher m = VENCIMIENTO.matcher(texto == null ? "" : texto.trim());
        if (!m.matches()) {
            return null;
        }
        int mes = Integer.parseInt(m.group(1));
        if (mes < 1 || mes > 12) {
            return null;
        }
        return YearMonth.of(2000 + Integer.parseInt(m.group(2)), mes);
    }

    /** Devuelve el mensaje de error del CVV, o null si es válido. */
    public static String validarCvv(String cvv, MarcaTarjeta marca) {
        String digitos = soloDigitos(cvv);
        if (digitos.isEmpty()) {
            return "Ingresa el código de seguridad.";
        }
        if (marca != null && digitos.length() != marca.getLargoCvv()) {
            return "El código de seguridad debe tener " + marca.getLargoCvv() + " dígitos.";
        }
        if (marca == null && (digitos.length() < 3 || digitos.length() > 4)) {
            return "El código de seguridad debe tener 3 o 4 dígitos.";
        }
        return null;
    }

    /**
     * Valida una tarjeta nueva. Devuelve los errores por campo; vacío si es válida.
     * cvv: null cuando no se pide (al guardar una tarjeta en la cuenta).
     */
    public static Map<String, String> validar(TarjetaRequest tarjeta, String cvv, boolean pedirCvv, LocalDate hoy) {
        Map<String, String> errores = new LinkedHashMap<>();
        if (tarjeta == null) {
            errores.put("tarjeta", "Ingresa los datos de la tarjeta.");
            return errores;
        }
        String digitos = soloDigitos(tarjeta.numero());
        MarcaTarjeta marca = MarcaTarjeta.detectar(digitos);

        if (digitos.isEmpty()) {
            errores.put("tarjeta.numero", "Ingresa el número de la tarjeta.");
        } else if (marca == null) {
            errores.put("tarjeta.numero", "Tarjeta no reconocida. Aceptamos Visa, Mastercard, American Express y Diners Club.");
        } else if (!marca.largoValido(digitos.length())) {
            errores.put("tarjeta.numero", "Al número de la tarjeta le faltan dígitos.");
        } else if (!pasaLuhn(digitos)) {
            errores.put("tarjeta.numero", "El número de la tarjeta no es válido. Revisa que esté bien escrito.");
        }

        if (tarjeta.titular() == null || !TITULAR.matcher(tarjeta.titular().trim()).matches()) {
            errores.put("tarjeta.titular", "Ingresa el nombre como aparece en la tarjeta.");
        }

        YearMonth vencimiento = leerVencimiento(tarjeta.vencimiento());
        if (vencimiento == null) {
            errores.put("tarjeta.vencimiento", "Usa el formato MM/AA.");
        } else if (vencimiento.isBefore(YearMonth.from(hoy))) {
            errores.put("tarjeta.vencimiento", "La tarjeta está vencida.");
        } else if (vencimiento.getYear() > hoy.getYear() + 20) {
            errores.put("tarjeta.vencimiento", "Revisa el año de vencimiento.");
        }

        if (pedirCvv) {
            String errorCvv = validarCvv(cvv, marca);
            if (errorCvv != null) {
                errores.put("cvv", errorCvv);
            }
        }
        return errores;
    }
}
