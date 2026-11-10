package cl.sanmarcos.vetsm.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados de una cita y las transiciones permitidas entre ellos:
 * PENDIENTE -> CONFIRMADA -> COMPLETADA, y se puede cancelar mientras no esté completada.
 */
public enum EstadoCita {
    PENDIENTE,
    CONFIRMADA,
    COMPLETADA,
    CANCELADA;

    /** Estados a los que se puede pasar desde el estado actual. */
    public Set<EstadoCita> siguientesPermitidos() {
        return switch (this) {
            case PENDIENTE -> EnumSet.of(CONFIRMADA, CANCELADA);
            case CONFIRMADA -> EnumSet.of(COMPLETADA, CANCELADA);
            case COMPLETADA, CANCELADA -> EnumSet.noneOf(EstadoCita.class);
        };
    }

    public boolean puedePasarA(EstadoCita nuevo) {
        return siguientesPermitidos().contains(nuevo);
    }

    /** Una cita completada o cancelada ya no se puede modificar. */
    public boolean esFinal() {
        return siguientesPermitidos().isEmpty();
    }
}
