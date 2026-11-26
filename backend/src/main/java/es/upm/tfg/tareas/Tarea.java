package es.upm.tfg.tareas;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/**
 * Definición de una tarea (RF-06). Lo que el hijo hace cada día es una {@link Asignacion}; la tarea
 * solo dice qué, cuánto se paga, cuándo se repite y a qué hijos va.
 */
@Entity
@Table(name = "tarea")
public class Tarea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long familiaId;

    private String titulo;

    private String descripcion;

    private Long recompensa;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private Periodicidad periodicidad;

    private Short diasSemana;

    private LocalDate fechaLimite;

    private String icono;

    @Column(updatable = false)
    private Long creadaPor;

    @Column(updatable = false)
    private Instant creadaEn = Instant.now();

    private Instant archivadaEn;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "tarea_hijo", joinColumns = @JoinColumn(name = "tarea_id"))
    @Column(name = "hijo_id")
    private Set<Long> hijos = new HashSet<>();

    protected Tarea() {
    }

    Tarea(long familiaId, Periodicidad periodicidad, long creadaPor) {
        this.familiaId = familiaId;
        this.periodicidad = periodicidad;
        this.creadaPor = creadaPor;
    }

    boolean estaArchivada() {
        return archivadaEn != null;
    }

    void archivar() {
        if (archivadaEn == null) {
            archivadaEn = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getFamiliaId() {
        return familiaId;
    }

    public String getTitulo() {
        return titulo;
    }

    void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Long getRecompensa() {
        return recompensa;
    }

    void setRecompensa(Long recompensa) {
        this.recompensa = recompensa;
    }

    public Periodicidad getPeriodicidad() {
        return periodicidad;
    }

    public Short getDiasSemana() {
        return diasSemana;
    }

    void setDiasSemana(Short diasSemana) {
        this.diasSemana = diasSemana;
    }

    public LocalDate getFechaLimite() {
        return fechaLimite;
    }

    void setFechaLimite(LocalDate fechaLimite) {
        this.fechaLimite = fechaLimite;
    }

    public String getIcono() {
        return icono;
    }

    void setIcono(String icono) {
        this.icono = icono;
    }

    public Set<Long> getHijos() {
        return hijos;
    }

    void setHijos(Set<Long> hijos) {
        this.hijos.clear();
        this.hijos.addAll(hijos);
    }
}
