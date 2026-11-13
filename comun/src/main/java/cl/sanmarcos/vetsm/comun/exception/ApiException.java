package cl.sanmarcos.vetsm.comun.exception;

import java.util.Map;

/**
 * Error controlado de la API: lleva el código HTTP que debe responderse.
 * Las subclases nombran cada caso para que el código de los servicios se lea claro.
 */
public abstract class ApiException extends RuntimeException {

    private final int status;
    private final Map<String, String> errores;

    protected ApiException(int status, String mensaje, Map<String, String> errores) {
        super(mensaje);
        this.status = status;
        this.errores = errores;
    }

    public int getStatus() {
        return status;
    }

    public Map<String, String> getErrores() {
        return errores;
    }

    /** 401: no hay sesión válida (credenciales incorrectas, token vencido o revocado). */
    public static class NoAutorizado extends ApiException {
        public NoAutorizado(String mensaje) {
            super(401, mensaje, null);
        }
    }

    /** 404: el registro pedido no existe. */
    public static class NoEncontrado extends ApiException {
        public NoEncontrado(String mensaje) {
            super(404, mensaje, null);
        }
    }

    /** 409: la operación choca con el estado actual de los datos (duplicado, horario ocupado, sin stock...). */
    public static class Conflicto extends ApiException {
        public Conflicto(String mensaje) {
            super(409, mensaje, null);
        }
    }

    /** 422: los datos tienen el formato correcto, pero una regla de negocio no lo permite. */
    public static class ReglaNegocio extends ApiException {
        public ReglaNegocio(String mensaje) {
            super(422, mensaje, null);
        }
    }

    /** 400: datos inválidos detectados en el servicio (por ejemplo, una tarjeta mal escrita). */
    public static class DatosInvalidos extends ApiException {
        public DatosInvalidos(Map<String, String> errores) {
            super(400, "Revisa los datos ingresados.", errores);
        }
    }

    /** 503: otro microservicio del que depende esta operación no responde. */
    public static class ServicioNoDisponible extends ApiException {
        public ServicioNoDisponible(String servicio) {
            super(503, "El servicio de " + servicio + " no está disponible en este momento. Inténtalo en unos minutos.", null);
        }
    }

    /**
     * Error que respondió otro microservicio. Se reenvía con el mismo código y mensaje, así el
     * frontend ve el motivo real (por ejemplo, "Stock insuficiente") y no un error genérico.
     */
    public static class DeOtroServicio extends ApiException {
        public DeOtroServicio(int status, String mensaje, Map<String, String> errores) {
            super(status, mensaje, errores);
        }
    }

    /** 402: la pasarela de pago rechazó el cobro. */
    public static class PagoRechazado extends ApiException {
        public PagoRechazado(String mensaje) {
            super(402, mensaje, null);
        }
    }
}
