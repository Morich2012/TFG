package es.upm.tfg.tareas;

/**
 * Evento TAREA_APROBADA: se publica en la misma transacción que el pago. En la iteración 4 lo
 * escucharán la experiencia y los logros (RF-22, RF-23) sin que este módulo los conozca.
 */
public record TareaAprobada(long familiaId, long asignacionId, long hijoId, long recompensa, long movimientoId) {
}
