package es.upm.tfg.familia;

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
 * Adulto o hijo de una familia. Los adultos entran con correo y contraseña; de los hijos solo
 * se guarda nombre o apodo, avatar, año de nacimiento y, si lo tienen, el hash del PIN (RNF-05).
 */
@Entity
@Table(name = "miembro")
public class Miembro {

    public static final String AVATAR_POR_DEFECTO = "avatar-1";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(updatable = false)
    private Long familiaId;

    @Enumerated(EnumType.STRING)
    private Rol rol;

    private String nombre;

    private String avatar = AVATAR_POR_DEFECTO;

    private String email;

    private String hashContrasena;

    private Short anioNacimiento;

    private String hashPin;

    private Integer experiencia = 0;

    private Short nivel = 1;

    private Short etapaFijada;

    private Instant consentimientoEn;

    @Column(updatable = false)
    private Instant creadoEn = Instant.now();

    private Instant dadoDeBajaEn;

    protected Miembro() {
    }

    public static Miembro adulto(long familiaId, Rol rol, String nombre, String email, String hashContrasena) {
        if (!rol.esAdulto()) {
            throw new IllegalArgumentException("Un adulto no puede tener rol HIJO");
        }
        Miembro m = new Miembro();
        m.familiaId = familiaId;
        m.rol = rol;
        m.nombre = nombre;
        m.email = email;
        m.hashContrasena = hashContrasena;
        m.consentimientoEn = Instant.now(); // acepta la política de privacidad al registrarse
        return m;
    }

    public static Miembro hijo(long familiaId, String nombre, String avatar, Short anioNacimiento, String hashPin) {
        Miembro m = new Miembro();
        m.familiaId = familiaId;
        m.rol = Rol.HIJO;
        m.nombre = nombre;
        m.avatar = avatar;
        m.anioNacimiento = anioNacimiento;
        m.hashPin = hashPin;
        m.consentimientoEn = Instant.now(); // RNF-06: el adulto consiente al darlo de alta
        return m;
    }

    /**
     * Baja (RF-05, RNF-06): borra los datos personales pero conserva la fila, porque el libro
     * contable es de solo inserción y sus movimientos apuntan al miembro.
     */
    public void anonimizar(String hashInutilizable) {
        this.nombre = "Miembro dado de baja";
        this.avatar = AVATAR_POR_DEFECTO;
        this.anioNacimiento = null;
        this.hashPin = null;
        if (rol.esAdulto()) {
            // La restricción adulto_con_credenciales exige correo y contraseña en los adultos.
            this.email = "baja-" + id + "@anonimo.invalid";
            this.hashContrasena = hashInutilizable;
        }
        this.dadoDeBajaEn = Instant.now();
    }

    public boolean estaActivo() {
        return dadoDeBajaEn == null;
    }

    public Long getId() {
        return id;
    }

    public Long getFamiliaId() {
        return familiaId;
    }

    public Rol getRol() {
        return rol;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getEmail() {
        return email;
    }

    public String getHashContrasena() {
        return hashContrasena;
    }

    public Short getAnioNacimiento() {
        return anioNacimiento;
    }

    public void setAnioNacimiento(Short anioNacimiento) {
        this.anioNacimiento = anioNacimiento;
    }

    public String getHashPin() {
        return hashPin;
    }

    public void setHashPin(String hashPin) {
        this.hashPin = hashPin;
    }

    public Integer getExperiencia() {
        return experiencia;
    }

    public Short getNivel() {
        return nivel;
    }

    public Short getEtapaFijada() {
        return etapaFijada;
    }

    public Instant getConsentimientoEn() {
        return consentimientoEn;
    }

    public Instant getDadoDeBajaEn() {
        return dadoDeBajaEn;
    }
}
