package es.upm.tfg.tareas;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Una vez que un hijo tiene que hacer una tarea (RF-07). Las de las tareas periódicas se crean al
 * consultar el día; la de una tarea única, al crearla. Guarda la recompensa pactada en ese momento.
 * Las filas se insertan con SQL ({@link ServicioAsignaciones}); aquí solo cambian de estado.
 */
@Entity
@Table(name = "asignacion_tarea")
public class Asignacion {

    @Id
    private Long id;

    @Column(updatable = false)
    private Long familiaId;

    @Column(updatable = false)
    private Long tareaId;

    @Column(updatable = false)
    private Long hijoId;

    @Column(updatable = false)
    private LocalDate fecha;

    @Column(updatable = false)
    private Long recompensa;

    @Enumerated(EnumType.STRING)
    private EstadoAsignacion estado;

    private Instant hechaEn;

    private Long movimientoId;

    @Version
    private Integer version;

    protected Asignacion() {
    }

    void entregar() {
        estado = EstadoAsignacion.HECHA;
        hechaEn = Instant.now();
    }

    void aprobar(long movimientoId) {
        estado = EstadoAsignacion.APROBADA;
        this.movimientoId = movimientoId;
    }

    void rechazar() {
        estado = EstadoAsignacion.PENDIENTE;
    }

    public Long getId() {
        return id;
    }

    public Long getFamiliaId() {
        return familiaId;
    }

    public Long getTareaId() {
        return tareaId;
    }

    public Long getHijoId() {
        return hijoId;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public Long getRecompensa() {
        return recompensa;
    }

    public EstadoAsignacion getEstado() {
        return estado;
    }
}
