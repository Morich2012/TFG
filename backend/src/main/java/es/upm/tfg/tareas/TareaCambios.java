package es.upm.tfg.tareas;

import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code PATCH /tareas/{tareaId}}: solo cambian los campos que vienen. La periodicidad no
 * se puede cambiar (para eso se crea otra tarea). Las asignaciones ya creadas conservan su
 * recompensa pactada.
 */
public record TareaCambios(
        @Size(min = 1, max = 80) String titulo,
        @Size(max = 500) String descripcion,
        @Positive Long recompensa,
        Periodicidad periodicidad,
        Set<DiaSemana> diasSemana,
        LocalDate fechaLimite,
        @Size(min = 1) Set<@Positive Long> hijos,
        @Size(min = 1, max = 40) String icono) {

    boolean vacio() {
        return titulo == null && descripcion == null && recompensa == null && periodicidad == null
                && diasSemana == null && fechaLimite == null && hijos == null && icono == null;
    }
}
