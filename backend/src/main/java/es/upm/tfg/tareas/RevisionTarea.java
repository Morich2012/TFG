package es.upm.tfg.tareas;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Cada aprobación o rechazo de una asignación, con quién la revisó y el comentario (RF-08). */
@Entity
@Table(name = "revision_tarea")
public class RevisionTarea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long asignacionId;

    @Enumerated(EnumType.STRING)
    private ResultadoRevision resultado;

    private String comentario;

    private Long revisadaPor;

    private Instant revisadaEn = Instant.now();

    protected RevisionTarea() {
    }

    RevisionTarea(long asignacionId, ResultadoRevision resultado, String comentario, long revisadaPor) {
        this.asignacionId = asignacionId;
        this.resultado = resultado;
        this.comentario = comentario;
        this.revisadaPor = revisadaPor;
    }
}
