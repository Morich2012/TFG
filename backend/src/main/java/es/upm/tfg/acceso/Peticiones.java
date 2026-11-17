package es.upm.tfg.acceso;

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

    public record LoginPeticion(
            @NotBlank String email,
            @NotBlank String contrasena,
            @Size(max = 60) String nombreDispositivo) {
    }

    public record VincularPeticion(
            @NotBlank @Size(max = 20) String codigo,
            @NotBlank @Size(max = 60) String nombreDispositivo) {
    }

    public record UnirsePeticion(
            @NotBlank @Size(max = 20) String codigo,
            @NotBlank @Size(max = 40) String nombre,
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull @Size(min = 8, max = 72) String contrasena,
            @NotNull @AssertTrue(message = "hay que aceptar la política de privacidad") Boolean aceptaPrivacidad,
            @Size(max = 60) String nombreDispositivo) {
    }

    public record RefrescarPeticion(
            @NotBlank String tokenRefresco,
            @Pattern(regexp = "^[0-9]{4,6}$", message = "el PIN debe tener de 4 a 6 cifras") String pin) {
    }

    public record TokenPushPeticion(@NotBlank @Size(max = 255) String tokenPush) {
    }
}
