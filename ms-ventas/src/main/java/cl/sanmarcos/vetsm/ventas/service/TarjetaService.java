package cl.sanmarcos.vetsm.ventas.service;

import cl.sanmarcos.vetsm.ventas.dto.TarjetaRequest;
import cl.sanmarcos.vetsm.ventas.dto.TarjetaResponse;
import cl.sanmarcos.vetsm.comun.exception.ApiException;
import cl.sanmarcos.vetsm.ventas.model.MarcaTarjeta;
import cl.sanmarcos.vetsm.ventas.model.Tarjeta;
import cl.sanmarcos.vetsm.ventas.repository.TarjetaRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tarjetas guardadas de un cliente. Todas las operaciones reciben al dueño: un cliente nunca puede
 * ver, usar ni borrar tarjetas de otra persona. Nunca se guarda el número completo ni el CVV.
 */
@Service
@Transactional
public class TarjetaService {

    private final TarjetaRepository tarjetas;
    private final Clock reloj;

    public TarjetaService(TarjetaRepository tarjetas, Clock reloj) {
        this.tarjetas = tarjetas;
        this.reloj = reloj;
    }

    /** Resultado de guardar: la tarjeta y si ya estaba guardada de antes. */
    public record Guardado(TarjetaResponse tarjeta, boolean duplicada) {
    }

    @Transactional(readOnly = true)
    public List<TarjetaResponse> listar(Long usuarioId) {
        LocalDate hoy = LocalDate.now(reloj);
        return tarjetas.findByUsuarioIdOrderByPredeterminadaDescIdDesc(usuarioId).stream()
                .map(t -> TarjetaResponse.desde(t, hoy))
                .toList();
    }

    /** Valida y guarda una tarjeta (sin CVV). Si ya estaba guardada no la repite. La primera queda como predeterminada. */
    public Guardado guardar(Long usuarioId, TarjetaRequest datos) {
        LocalDate hoy = LocalDate.now(reloj);
        Map<String, String> errores = ValidadorTarjeta.validar(datos, null, false, hoy);
        if (!errores.isEmpty()) {
            throw new ApiException.DatosInvalidos(errores);
        }
        String digitos = ValidadorTarjeta.soloDigitos(datos.numero());
        MarcaTarjeta marca = MarcaTarjeta.detectar(digitos);
        String ultimos4 = digitos.substring(digitos.length() - 4);
        YearMonth vencimiento = ValidadorTarjeta.leerVencimiento(datos.vencimiento());

        List<Tarjeta> actuales = tarjetas.findByUsuarioIdOrderByPredeterminadaDescIdDesc(usuarioId);
        for (Tarjeta t : actuales) {
            if (t.getMarca() == marca && t.getUltimos4().equals(ultimos4)
                    && t.getMesVencimiento() == vencimiento.getMonthValue() && t.getAnioVencimiento() == vencimiento.getYear()) {
                return new Guardado(TarjetaResponse.desde(t, hoy), true);
            }
        }

        Tarjeta nueva = new Tarjeta(usuarioId, marca, ultimos4, datos.titular().trim().toUpperCase(),
                vencimiento.getMonthValue(), vencimiento.getYear());
        nueva.setPredeterminada(actuales.isEmpty());
        return new Guardado(TarjetaResponse.desde(tarjetas.save(nueva), hoy), false);
    }

    public List<TarjetaResponse> marcarPredeterminada(Long usuarioId, Long tarjetaId) {
        Tarjeta elegida = buscarDe(usuarioId, tarjetaId);
        if (elegida.estaVencida(LocalDate.now(reloj))) {
            throw new ApiException.ReglaNegocio("No se puede elegir como predeterminada una tarjeta vencida.");
        }
        tarjetas.findByUsuarioIdOrderByPredeterminadaDescIdDesc(usuarioId)
                .forEach(t -> t.setPredeterminada(t.getId().equals(tarjetaId)));
        return listar(usuarioId);
    }

    /** Si se elimina la predeterminada, la más reciente que quede pasa a serlo. */
    public void eliminar(Long usuarioId, Long tarjetaId) {
        Tarjeta tarjeta = buscarDe(usuarioId, tarjetaId);
        tarjetas.delete(tarjeta);
        tarjetas.flush();
        if (tarjeta.isPredeterminada()) {
            tarjetas.findByUsuarioIdOrderByPredeterminadaDescIdDesc(usuarioId).stream()
                    .findFirst()
                    .ifPresent(t -> t.setPredeterminada(true));
        }
    }

    /** Al eliminar una cuenta en ms-usuarios se borran también sus tarjetas guardadas. */
    public void eliminarTodas(Long usuarioId) {
        tarjetas.deleteByUsuarioId(usuarioId);
    }

    /** Busca la tarjeta solo entre las del usuario: si es de otra persona, responde como si no existiera. */
    public Tarjeta buscarDe(Long usuarioId, Long tarjetaId) {
        return tarjetas.findByIdAndUsuarioId(tarjetaId, usuarioId)
                .orElseThrow(() -> new ApiException.NoEncontrado("No tienes una tarjeta guardada con el id " + tarjetaId + "."));
    }
}
