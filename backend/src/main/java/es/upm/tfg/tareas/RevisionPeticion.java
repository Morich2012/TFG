package es.upm.tfg.tareas;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Esquema {@code RevisionPeticion} (RF-08): el comentario es obligatorio al rechazar. */
public record RevisionPeticion(@NotNull ResultadoRevision resultado, @Size(max = 280) String comentario) {
}
