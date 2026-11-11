package cl.sanmarcos.vetsm.model;

import java.util.Set;

/** Marcas de tarjeta aceptadas, con los largos de número y de código de seguridad (CVV) de cada una. */
public enum MarcaTarjeta {
    VISA("Visa", Set.of(13, 16, 19), 3),
    MASTERCARD("Mastercard", Set.of(16), 3),
    AMEX("American Express", Set.of(15), 4),
    DINERS("Diners Club", Set.of(14, 16), 3);

    private final String nombre;
    private final Set<Integer> largos;
    private final int largoCvv;

    MarcaTarjeta(String nombre, Set<Integer> largos, int largoCvv) {
        this.nombre = nombre;
        this.largos = largos;
        this.largoCvv = largoCvv;
    }

    public String getNombre() {
        return nombre;
    }

    public boolean largoValido(int largo) {
        return largos.contains(largo);
    }

    public int getLargoCvv() {
        return largoCvv;
    }

    /** Reconoce la marca por los primeros dígitos del número. Devuelve null si no se reconoce. */
    public static MarcaTarjeta detectar(String digitos) {
        if (digitos == null || digitos.isEmpty()) {
            return null;
        }
        if (digitos.startsWith("4")) {
            return VISA;
        }
        if (digitos.matches("^(5[1-5]|222[1-9]|22[3-9]\\d|2[3-6]\\d\\d|27[01]\\d|2720).*")) {
            return MASTERCARD;
        }
        if (digitos.matches("^3[47].*")) {
            return AMEX;
        }
        if (digitos.matches("^3(0[0-5]|[68]).*")) {
            return DINERS;
        }
        return null;
    }
}
