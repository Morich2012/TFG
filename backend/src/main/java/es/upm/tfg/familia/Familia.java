package es.upm.tfg.familia;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "familia")
public class Familia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;

    private String nombreMoneda = "monedas";

    /** Cuántas monedas equivalen a 1 euro (RF-35). */
    private Integer monedasPorEuro = 10;

    @Column(updatable = false)
    private Instant creadaEn = Instant.now();

    protected Familia() {
    }

    public Familia(String nombre) {
        this.nombre = nombre;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getNombreMoneda() {
        return nombreMoneda;
    }

    public void setNombreMoneda(String nombreMoneda) {
        this.nombreMoneda = nombreMoneda;
    }

    public Integer getMonedasPorEuro() {
        return monedasPorEuro;
    }

    public void setMonedasPorEuro(Integer monedasPorEuro) {
        this.monedasPorEuro = monedasPorEuro;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }
}
