package es.upm.tfg.acceso;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Dispositivo vinculado de un hijo o sesión de un adulto. Guarda el hash del token de refresco,
 * que se rota en cada uso; el token de acceso (JWT) no se guarda.
 */
@Entity
@Table(name = "dispositivo")
public class Dispositivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long miembroId;

    private String nombre;

    private String hashTokenRefresco;

    private String tokenPush;

    @Column(updatable = false)
    private Instant creadoEn = Instant.now();

    private Instant ultimoUsoEn;

    private Instant revocadoEn;

    protected Dispositivo() {
    }

    Dispositivo(long miembroId, String nombre, String hashTokenRefresco) {
        this.miembroId = miembroId;
        this.nombre = nombre;
        this.hashTokenRefresco = hashTokenRefresco;
        this.ultimoUsoEn = creadoEn;
    }

    void rotarToken(String nuevoHash) {
        this.hashTokenRefresco = nuevoHash;
        this.ultimoUsoEn = Instant.now();
    }

    void revocar() {
        if (revocadoEn == null) {
            revocadoEn = Instant.now();
        }
    }

    boolean estaActivo() {
        return revocadoEn == null;
    }

    void setTokenPush(String tokenPush) {
        this.tokenPush = tokenPush;
    }

    public Long getId() {
        return id;
    }

    public Long getMiembroId() {
        return miembroId;
    }

    public String getNombre() {
        return nombre;
    }

    public String getTokenPush() {
        return tokenPush;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public Instant getUltimoUsoEn() {
        return ultimoUsoEn;
    }

    public Instant getRevocadoEn() {
        return revocadoEn;
    }
}
