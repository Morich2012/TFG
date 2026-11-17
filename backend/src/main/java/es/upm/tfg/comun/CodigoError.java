package es.upm.tfg.comun;

import org.springframework.http.HttpStatus;

/**
 * Códigos de error estables de la API (campo {@code codigo} del Problema, RFC 9457).
 * La app los traduce a mensajes pensados para niños.
 */
public enum CodigoError {
    DATOS_INVALIDOS(HttpStatus.BAD_REQUEST, "Datos no válidos"),
    NO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "No autenticado"),
    PROHIBIDO(HttpStatus.FORBIDDEN, "Operación no permitida"),
    NO_ENCONTRADO(HttpStatus.NOT_FOUND, "No encontrado"),
    CORREO_EN_USO(HttpStatus.CONFLICT, "El correo ya está registrado"),
    CODIGO_CADUCADO(HttpStatus.GONE, "Código caducado o ya usado"),
    DEMASIADOS_INTENTOS(HttpStatus.TOO_MANY_REQUESTS, "Demasiados intentos"),
    ESTADO_INVALIDO(HttpStatus.CONFLICT, "La operación no es válida en el estado actual");

    private final HttpStatus estado;
    private final String titulo;

    CodigoError(HttpStatus estado, String titulo) {
        this.estado = estado;
        this.titulo = titulo;
    }

    public HttpStatus estado() {
        return estado;
    }

    public String titulo() {
        return titulo;
    }
}
