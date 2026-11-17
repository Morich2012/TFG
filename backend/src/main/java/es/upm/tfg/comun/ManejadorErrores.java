package es.upm.tfg.comun;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Convierte todos los errores en {@code application/problem+json} (RFC 9457) con el campo
 * {@code codigo} que la app usa para elegir el mensaje.
 */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ErrorApi.class)
    ResponseEntity<ProblemDetail> errorDeNegocio(ErrorApi e) {
        return ResponseEntity.status(e.codigo().estado()).body(problema(e.codigo(), e.getMessage()));
    }

    /** Un rol sin permiso para la operación (anotaciones @PreAuthorize). */
    @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
    ResponseEntity<ProblemDetail> prohibido(RuntimeException e) {
        CodigoError codigo = CodigoError.PROHIBIDO;
        return ResponseEntity.status(codigo.estado()).body(problema(codigo, "Tu rol no permite esta operación"));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problema = problema(CodigoError.DATOS_INVALIDOS, "Revisa los campos marcados");
        List<Map<String, String>> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("campo", f.getField(),
                        "mensaje", f.getDefaultMessage() == null ? "no válido" : f.getDefaultMessage()))
                .toList();
        problema.setProperty("errores", errores);
        return ResponseEntity.badRequest().body(problema);
    }

    /** Añade el código a los errores que genera Spring (JSON mal formado, método no soportado...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> respuesta = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (respuesta != null && respuesta.getBody() instanceof ProblemDetail problema
                && (problema.getProperties() == null || !problema.getProperties().containsKey("codigo"))) {
            problema.setProperty("codigo", codigoPara(statusCode).name());
        }
        return respuesta;
    }

    public static ProblemDetail problema(CodigoError codigo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(codigo.estado(), detalle);
        problema.setTitle(codigo.titulo());
        problema.setProperty("codigo", codigo.name());
        return problema;
    }

    private static CodigoError codigoPara(HttpStatusCode estado) {
        return switch (estado.value()) {
            case 400 -> CodigoError.DATOS_INVALIDOS;
            case 401 -> CodigoError.NO_AUTENTICADO;
            case 403 -> CodigoError.PROHIBIDO;
            case 404 -> CodigoError.NO_ENCONTRADO;
            default -> CodigoError.ESTADO_INVALIDO;
        };
    }
}
