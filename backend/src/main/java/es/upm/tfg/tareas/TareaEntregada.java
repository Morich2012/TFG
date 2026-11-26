package es.upm.tfg.tareas;

/** Evento: un hijo ha entregado una tarea. Servirá para avisar a los adultos (RF-28). */
public record TareaEntregada(long familiaId, long asignacionId, long hijoId) {
}
