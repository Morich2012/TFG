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
 * Monedero, hucha o banco de la familia. El saldo es de solo lectura para Java: lo mantiene el
 * disparador {@code movimiento_saldos} de la base de datos al insertar cada movimiento.
 * Los campos de las huchas se añadirán en la iteración 3.
 */
@Entity
@Table(name = "cuenta")
public class Cuenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long familiaId;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private TipoCuenta tipo;

    @Column(updatable = false)
    private Long titularId;

    private String nombre;

    @Column(insertable = false, updatable = false)
    private Long saldo;

    @Column(updatable = false)
    private Instant creadaEn = Instant.now();

    private Instant cerradaEn;

    protected Cuenta() {
    }

    Cuenta(long familiaId, TipoCuenta tipo, Long titularId, String nombre) {
        this.familiaId = familiaId;
        this.tipo = tipo;
        this.titularId = titularId;
        this.nombre = nombre;
    }

    public Long getId() {
        return id;
    }

    public Long getFamiliaId() {
        return familiaId;
    }

    public TipoCuenta getTipo() {
        return tipo;
    }

    public Long getTitularId() {
        return titularId;
    }

    public String getNombre() {
        return nombre;
    }

    public Long getSaldo() {
        return saldo;
    }

    public Instant getCerradaEn() {
        return cerradaEn;
    }
}
