package cl.sanmarcos.vetsm.model;

/** Lo que se puede agregar al carrito: una atención de la clínica o un artículo de la tienda. */
public enum TipoProducto {
    /** Atención veterinaria (consulta, vacunación, cirugía...). No maneja stock y se puede agendar como cita. */
    SERVICIO,
    /** Artículo físico (alimento, accesorios...). Maneja stock. */
    PRODUCTO
}
