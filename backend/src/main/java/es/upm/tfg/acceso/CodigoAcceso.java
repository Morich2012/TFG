package es.upm.tfg.acceso;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Código de un solo uso y con caducidad. Solo se guarda su hash. */
@Entity
@Table(name = "codigo_acceso")
public class CodigoAcceso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long familiaId;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private TipoCodigo tipo;

    /** Hijo a vincular (solo VINCULACION). */
    @Column(updatable = false)
    private Long miembroId;

    @Column(updatable = false)
    private String hashCodigo;

    @Column(updatable = false)
    private Long creadoPor;

    @Column(updatable = false)
    private Instant expiraEn;

    private Instant usadoEn;

    protected CodigoAcceso() {
    }

    CodigoAcceso(long familiaId, TipoCodigo tipo, Long miembroId, String hashCodigo, long creadoPor,
            Instant expiraEn) {
        this.familiaId = familiaId;
        this.tipo = tipo;
        this.miembroId = miembroId;
        this.hashCodigo = hashCodigo;
        this.creadoPor = creadoPor;
        this.expiraEn = expiraEn;
    }

    boolean sePuedeUsar(TipoCodigo tipoEsperado, Instant ahora) {
        return tipo == tipoEsperado && usadoEn == null && ahora.isBefore(expiraEn);
    }

    void marcarUsado(Instant ahora) {
        this.usadoEn = ahora;
    }

    public Long getFamiliaId() {
        return familiaId;
    }

    public Long getMiembroId() {
        return miembroId;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }
}
