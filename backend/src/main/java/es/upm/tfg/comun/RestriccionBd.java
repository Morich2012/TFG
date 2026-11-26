package es.upm.tfg.comun;

import org.hibernate.exception.ConstraintViolationException;

/**
 * Averigua qué restricción de la base de datos ha rechazado una operación, para traducirla a un
 * código de la API. Algunas reglas solo las comprueba PostgreSQL (por ejemplo, que un saldo no
 * quede en negativo cuando dos compras llegan a la vez), así que el servicio tiene que reconocerlas.
 */
public final class RestriccionBd {

    private RestriccionBd() {
    }

    /** {@code true} si la causa del error es la restricción con ese nombre. */
    public static boolean es(Throwable error, String restriccion) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve && restriccion.equals(cve.getConstraintName())) {
                return true;
            }
            // Si la restricción salta dentro de un disparador, el nombre solo viene en el mensaje.
            if (t.getMessage() != null && t.getMessage().contains("\"" + restriccion + "\"")) {
                return true;
            }
        }
        return false;
    }
}
