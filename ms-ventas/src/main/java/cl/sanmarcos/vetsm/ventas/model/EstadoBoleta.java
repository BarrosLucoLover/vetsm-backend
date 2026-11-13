package cl.sanmarcos.vetsm.ventas.model;

public enum EstadoBoleta {
    /** Compra realizada. */
    PAGADA,
    /** Compra anulada por un administrador; el stock de sus productos se repone. */
    ANULADA
}
