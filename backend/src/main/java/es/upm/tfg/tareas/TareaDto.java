package es.upm.tfg.tareas;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/** Esquema {@code Tarea}. */
public record TareaDto(long id, String titulo, String descripcion, long recompensa, Periodicidad periodicidad,
        Set<DiaSemana> diasSemana, LocalDate fechaLimite, List<Long> hijos, String icono, boolean archivada) {

    static TareaDto de(Tarea t) {
        Set<DiaSemana> dias = t.getPeriodicidad() == Periodicidad.SEMANAL ? DiaSemana.deMascara(t.getDiasSemana())
                : null;
        return new TareaDto(t.getId(), t.getTitulo(), t.getDescripcion(), t.getRecompensa(), t.getPeriodicidad(),
                dias, t.getFechaLimite(), t.getHijos().stream().sorted().toList(), t.getIcono(), t.estaArchivada());
    }
}
