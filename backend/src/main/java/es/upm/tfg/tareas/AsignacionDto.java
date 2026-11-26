package es.upm.tfg.tareas;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Esquema {@code Asignacion}. {@code comentario} es el de la última revisión (el motivo de un rechazo). */
public record AsignacionDto(long id, long tareaId, long hijoId, LocalDate fecha, EstadoAsignacion estado,
        String titulo, String icono, long recompensa, OffsetDateTime hechaEn, Long movimientoId, String comentario) {
}
