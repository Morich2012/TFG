package es.upm.tfg.acceso;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cuerpos de las peticiones de acceso, con los mismos nombres que los esquemas de la OpenAPI. */
public final class Peticiones {

    private Peticiones() {
    }

    /** Contraseña de 8 a 72 caracteres: 72 bytes es el máximo que procesa BCrypt. */
    public record RegistroPeticion(
            @NotBlank @Size(max = 60) String nombreFamilia,
            @NotBlank @Size(max = 40) String nombre,
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull @Size(min = 8, max = 72) String contrasena,
            @NotNull @AssertTrue(message = "hay que aceptar la política de privacidad") Boolean aceptaPrivacidad) {
    }

    /**
     * Cuerpo de {@code POST /sesiones}. El campo {@code tipo} elige la forma de obtener la sesión,
     * como los <i>grant types</i> del endpoint de tokens de OAuth 2.0 (RFC 6749). Un tipo que no
     * existe o que falta responde 400.
     */
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "tipo")
    @JsonSubTypes({
        @JsonSubTypes.Type(value = SesionConContrasena.class, name = "CONTRASENA"),
        @JsonSubTypes.Type(value = SesionConTokenRefresco.class, name = "TOKEN_REFRESCO"),
        @JsonSubTypes.Type(value = SesionConCodigoVinculacion.class, name = "CODIGO_VINCULACION"),
        @JsonSubTypes.Type(value = SesionConCodigoInvitacion.class, name = "CODIGO_INVITACION")
    })
    public sealed interface SesionPeticion
            permits SesionConContrasena, SesionConTokenRefresco, SesionConCodigoVinculacion, SesionConCodigoInvitacion {
    }

    /** Un adulto entra con correo y contraseña. */
    public record SesionConContrasena(
            @NotBlank String email,
            @NotBlank String contrasena,
            @Size(max = 60) String nombreDispositivo) implements SesionPeticion {
    }

    /** Se cambia el token de refresco por uno nuevo; el PIN es opcional (si el hijo no usa huella). */
    public record SesionConTokenRefresco(
            @NotBlank String tokenRefresco,
            @Pattern(regexp = "^[0-9]{4,6}$", message = "el PIN debe tener de 4 a 6 cifras") String pin)
            implements SesionPeticion {
    }

    /** El móvil del hijo canjea el código del QR (RF-04). */
    public record SesionConCodigoVinculacion(
            @NotBlank @Size(max = 20) String codigo,
            @NotBlank @Size(max = 60) String nombreDispositivo) implements SesionPeticion {
    }

    /** Un segundo adulto canjea su invitación y se crea en la familia (RF-02). */
    public record SesionConCodigoInvitacion(
            @NotBlank @Size(max = 20) String codigo,
            @NotBlank @Size(max = 40) String nombre,
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull @Size(min = 8, max = 72) String contrasena,
            @NotNull @AssertTrue(message = "hay que aceptar la política de privacidad") Boolean aceptaPrivacidad,
            @Size(max = 60) String nombreDispositivo) implements SesionPeticion {
    }

    public record SesionCambios(@NotBlank @Size(max = 255) String tokenPush) {
    }
}
