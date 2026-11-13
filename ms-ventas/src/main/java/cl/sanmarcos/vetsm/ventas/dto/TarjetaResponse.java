package cl.sanmarcos.vetsm.ventas.dto;

import cl.sanmarcos.vetsm.ventas.model.MarcaTarjeta;
import cl.sanmarcos.vetsm.ventas.model.Tarjeta;
import java.time.LocalDate;

/** Tarjeta guardada: solo datos seguros. */
public record TarjetaResponse(
        Long id,
        MarcaTarjeta marca,
        String marcaNombre,
        String ultimos4,
        String titular,
        int mes,
        int anio,
        boolean predeterminada,
        boolean vencida) {

    public static TarjetaResponse desde(Tarjeta t, LocalDate hoy) {
        return new TarjetaResponse(t.getId(), t.getMarca(), t.getMarca().getNombre(), t.getUltimos4(), t.getTitular(),
                t.getMesVencimiento(), t.getAnioVencimiento(), t.isPredeterminada(), t.estaVencida(hoy));
    }
}
