package cl.sanmarcos.vetsm.comun.util;

/** Ayudas para limpiar los textos que llegan en las peticiones. */
public final class Textos {

    private Textos() {
    }

    /** Quita los espacios de los extremos; un texto vacío se guarda como null. */
    public static String limpiar(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
