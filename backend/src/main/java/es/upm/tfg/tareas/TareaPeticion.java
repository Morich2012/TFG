package es.upm.tfg.tareas;

import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Esquema {@code TareaPeticion} (RF-06). {@code plantillaId} solo sirve para tomar su icono. */
public record TareaPeticion(
        @NotBlank @Size(max = 80) String titulo,
        @Size(max = 500) String descripcion,
        @NotNull @Positive Long recompensa,
        @NotNull Periodicidad periodicidad,
        Set<DiaSemana> diasSemana,
        LocalDate fechaLimite,
        @NotEmpty Set<@NotNull @Positive Long> hijos,
        @Size(min = 1, max = 40) String icono,
        @Positive Long plantillaId) {
}
