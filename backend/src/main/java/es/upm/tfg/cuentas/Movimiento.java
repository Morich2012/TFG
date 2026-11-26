package es.upm.tfg.cuentas;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Apunte del libro contable (RF-10). Solo se inserta: ninguna columna es actualizable y el
 * disparador movimiento_sin_cambios rechaza cualquier UPDATE o DELETE (RNF-09). Al insertarlo,
 * el disparador movimiento_saldos actualiza los saldos de las dos cuentas.
 */
@Entity
@Table(name = "movimiento")
public class Movimiento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long familiaId;

    @Column(updatable = false)
    private Long cuentaOrigenId;

    @Column(updatable = false)
    private Long cuentaDestinoId;

    @Column(updatable = false)
    private Long cantidad;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private TipoMovimiento tipo;

    @Column(updatable = false)
    private Long categoriaId;

    @Column(updatable = false)
    private String concepto;

    @Column(updatable = false)
    private Long autorId;

    @Column(updatable = false)
    private String origenTipo;

    @Column(updatable = false)
    private Long origenId;

    @Column(name = "corrige_a_id", updatable = false)
    private Long corrigeAId;

    @Column(updatable = false)
    private String claveIdempotencia;

    /** Lo pone la base de datos (hora de la transacción). */
    @Column(insertable = false, updatable = false)
    private Instant creadoEn;

    protected Movimiento() {
    }

    Movimiento(NuevoMovimiento n) {
        this.familiaId = n.familiaId();
        this.cuentaOrigenId = n.cuentaOrigenId();
        this.cuentaDestinoId = n.cuentaDestinoId();
        this.cantidad = n.cantidad();
        this.tipo = n.tipo();
        this.categoriaId = n.categoriaId();
        this.concepto = n.concepto();
        this.autorId = n.autorId();
        this.origenTipo = n.origenTipo();
        this.origenId = n.origenId();
        this.corrigeAId = n.corrigeAId();
        this.claveIdempotencia = n.claveIdempotencia();
    }

    public Long getId() {
        return id;
    }

    public Long getFamiliaId() {
        return familiaId;
    }

    public Long getCuentaOrigenId() {
        return cuentaOrigenId;
    }

    public Long getCuentaDestinoId() {
        return cuentaDestinoId;
    }

    public Long getCantidad() {
        return cantidad;
    }

    public TipoMovimiento getTipo() {
        return tipo;
    }

    public String getConcepto() {
        return concepto;
    }

    public String getOrigenTipo() {
        return origenTipo;
    }

    public Long getOrigenId() {
        return origenId;
    }
}
