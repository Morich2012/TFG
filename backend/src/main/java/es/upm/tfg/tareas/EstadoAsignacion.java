package es.upm.tfg.tareas;

/**
 * Estado de una asignación. Al rechazarla vuelve a PENDIENTE para que el hijo la rehaga (el
 * motivo queda en revision_tarea); RECHAZADA existe en el esquema pero el backend no la usa.
 */
public enum EstadoAsignacion {
    PENDIENTE,
    HECHA,
    APROBADA,
    RECHAZADA,
    CADUCADA
}
