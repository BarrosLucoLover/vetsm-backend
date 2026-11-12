package cl.sanmarcos.vetsm.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Convierte cualquier error en una respuesta JSON con el formato de ErrorResponse y el código HTTP
 * adecuado. Así los controladores no tienen try/catch y el frontend recibe siempre el mismo formato.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> controlado(ApiException e, HttpServletRequest request) {
        return responder(e.getStatus(), e.getMessage(), e.getErrores(), request);
    }

    /** Validaciones de @Valid sobre el cuerpo JSON: un mensaje por campo. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException e, HttpServletRequest request) {
        Map<String, String> errores = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> errores.putIfAbsent(f.getField(), f.getDefaultMessage()));
        e.getBindingResult().getGlobalErrors().forEach(g -> errores.putIfAbsent(g.getObjectName(), g.getDefaultMessage()));
        return responder(400, "Revisa los datos ingresados.", errores, request);
    }

    /** Validaciones sobre parámetros de la URL (por ejemplo, un id negativo). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> validacionParametros(HandlerMethodValidationException e, HttpServletRequest request) {
        Map<String, String> errores = new LinkedHashMap<>();
        e.getParameterValidationResults().forEach(r -> r.getResolvableErrors().forEach(err ->
                errores.putIfAbsent(r.getMethodParameter().getParameterName(), err.getDefaultMessage())));
        return responder(400, "Revisa los datos ingresados.", errores, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> jsonInvalido(HttpMessageNotReadableException e, HttpServletRequest request) {
        return responder(400, "El cuerpo de la petición no es un JSON válido o tiene un dato con un formato incorrecto.", null, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> tipoIncorrecto(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return responder(400, "El valor '" + e.getValue() + "' no es válido para el parámetro '" + e.getName() + "'.", null, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> faltaParametro(MissingServletRequestParameterException e, HttpServletRequest request) {
        return responder(400, "Falta el parámetro '" + e.getParameterName() + "'.", null, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> metodoNoPermitido(HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        return responder(405, "El método " + e.getMethod() + " no está permitido en esta ruta.", null, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> rutaInexistente(NoResourceFoundException e, HttpServletRequest request) {
        return responder(404, "La ruta pedida no existe.", null, request);
    }

    /** 403: hay sesión, pero el rol no tiene permiso para ese endpoint (@PreAuthorize). */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> sinPermiso(AccessDeniedException e, HttpServletRequest request) {
        return responder(403, "Tu cuenta no tiene permiso para realizar esta acción.", null, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> sinSesion(AuthenticationException e, HttpServletRequest request) {
        return responder(401, "Debes iniciar sesión para acceder a este recurso.", null, request);
    }

    /** Dos personas modificaron el mismo registro a la vez (por ejemplo, el stock del último artículo). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> concurrencia(ObjectOptimisticLockingFailureException e, HttpServletRequest request) {
        return responder(409, "Otro usuario modificó estos datos al mismo tiempo. Vuelve a intentarlo.", null, request);
    }

    /** Restricción de la base de datos (por ejemplo, un correo duplicado que no detectó el servicio). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integridad(DataIntegrityViolationException e, HttpServletRequest request) {
        log.warn("Restricción de integridad en {}: {}", request.getRequestURI(), e.getMostSpecificCause().getMessage());
        return responder(409, "La operación no se puede completar porque entra en conflicto con otros datos.", null, request);
    }

    /** Cualquier error no previsto: se registra completo en el log, pero al cliente no se le muestran detalles internos. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> inesperado(Exception e, HttpServletRequest request) {
        log.error("Error inesperado en {} {}", request.getMethod(), request.getRequestURI(), e);
        return responder(500, "Ocurrió un error inesperado. Inténtalo nuevamente.", null, request);
    }

    private ResponseEntity<ErrorResponse> responder(int status, String mensaje, Map<String, String> errores, HttpServletRequest request) {
        HttpStatus estado = HttpStatus.resolve(status);
        String nombre = estado != null ? estado.getReasonPhrase() : "Error";
        ErrorResponse cuerpo = new ErrorResponse(Instant.now(), status, nombre, mensaje, request.getRequestURI(),
                errores == null || errores.isEmpty() ? null : errores);
        return ResponseEntity.status(status).body(cuerpo);
    }
}
