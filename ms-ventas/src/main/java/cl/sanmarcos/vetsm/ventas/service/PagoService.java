package cl.sanmarcos.vetsm.ventas.service;

import cl.sanmarcos.vetsm.ventas.dto.PagoRequest;
import cl.sanmarcos.vetsm.comun.exception.ApiException;
import cl.sanmarcos.vetsm.ventas.model.MarcaTarjeta;
import cl.sanmarcos.vetsm.ventas.model.Tarjeta;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Pasarela de pago SIMULADA: no se conecta a ningún banco ni realiza cobros reales.
 * Imita a una pasarela real: valida la tarjeta, puede rechazarla y, si aprueba, entrega un código
 * de autorización. Del medio de pago solo devuelve datos seguros (marca y últimos 4 dígitos).
 *
 * En producción se reemplazaría por una pasarela certificada (PCI DSS) como Webpay o Mercado Pago:
 * el número de la tarjeta lo recibiría ella y el servidor solo vería un token.
 */
@Service
public class PagoService {

    /** Número de prueba que el "banco" rechaza siempre, para poder mostrar un pago fallido. */
    static final Set<String> TARJETAS_RECHAZADAS = Set.of("4000000000000002");

    private final TarjetaService tarjetas;
    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();

    public PagoService(TarjetaService tarjetas, Clock reloj) {
        this.tarjetas = tarjetas;
        this.reloj = reloj;
    }

    /** Resultado de un cobro aprobado. */
    public record ResultadoPago(MarcaTarjeta marca, String ultimos4, String codigoAutorizacion) {
    }

    /** Medio de pago ya validado: solo datos seguros, más los dígitos para consultar al "banco". */
    record MedioValidado(MarcaTarjeta marca, String ultimos4, String digitos) {
    }

    /**
     * Revisa el medio de pago sin cobrar: tarjeta bien escrita, vigente, del propio cliente y con CVV.
     * La compra lo llama ANTES de reservar stock en ms-catalogo, así un error de tipeo no obliga a
     * reservar y devolver.
     */
    public void validar(Long clienteId, PagoRequest pago) {
        validarMedio(clienteId, pago);
    }

    public ResultadoPago cobrar(Long clienteId, PagoRequest pago, int monto) {
        if (monto <= 0) {
            throw new ApiException.ReglaNegocio("El monto a pagar no es válido.");
        }
        MedioValidado medio = validarMedio(clienteId, pago);
        if (medio.digitos() != null && TARJETAS_RECHAZADAS.contains(medio.digitos())) {
            throw new ApiException.PagoRechazado("El banco rechazó la tarjeta. Prueba con otra tarjeta o comunícate con tu banco.");
        }
        return new ResultadoPago(medio.marca(), medio.ultimos4(), codigoAutorizacion());
    }

    private MedioValidado validarMedio(Long clienteId, PagoRequest pago) {
        boolean usaGuardada = pago.tarjetaGuardadaId() != null;
        boolean usaNueva = pago.tarjeta() != null;
        if (usaGuardada == usaNueva) {
            throw new ApiException.DatosInvalidos(Map.of("pago", "Elige una tarjeta guardada o ingresa una nueva (solo una de las dos)."));
        }
        LocalDate hoy = LocalDate.now(reloj);

        if (usaGuardada) {
            Tarjeta guardada = tarjetas.buscarDe(clienteId, pago.tarjetaGuardadaId());
            if (guardada.estaVencida(hoy)) {
                throw new ApiException.ReglaNegocio("La tarjeta " + guardada.getMarca().getNombre() + " terminada en "
                        + guardada.getUltimos4() + " está vencida. Usa otra tarjeta.");
            }
            String errorCvv = ValidadorTarjeta.validarCvv(pago.cvv(), guardada.getMarca());
            if (errorCvv != null) {
                throw new ApiException.DatosInvalidos(Map.of("cvv", errorCvv));
            }
            return new MedioValidado(guardada.getMarca(), guardada.getUltimos4(), null);
        }

        Map<String, String> errores = ValidadorTarjeta.validar(pago.tarjeta(), pago.cvv(), true, hoy);
        if (!errores.isEmpty()) {
            throw new ApiException.DatosInvalidos(errores);
        }
        String digitos = ValidadorTarjeta.soloDigitos(pago.tarjeta().numero());
        return new MedioValidado(MarcaTarjeta.detectar(digitos), digitos.substring(digitos.length() - 4), digitos);
    }

    private String codigoAutorizacion() {
        return String.valueOf(100_000 + azar.nextInt(900_000));
    }
}
