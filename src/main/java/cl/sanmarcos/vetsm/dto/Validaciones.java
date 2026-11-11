package cl.sanmarcos.vetsm.dto;

/** Expresiones regulares compartidas por varios DTOs. */
final class Validaciones {

    /** Celular chileno, opcional: "", "+56 9 1234 5678", "56912345678" o "912345678". */
    static final String CELULAR = "^$|^(\\+?56)?\\s?9[\\s-]?\\d{4}[\\s-]?\\d{4}$";
    static final String MENSAJE_CELULAR = "Ingresa un celular válido, por ejemplo +56 9 1234 5678.";

    private Validaciones() {
    }
}
