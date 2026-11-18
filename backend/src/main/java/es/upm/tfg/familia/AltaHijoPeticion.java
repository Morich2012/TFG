package es.upm.tfg.familia;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Esquema {@code AltaHijoPeticion} (RF-03). */
public record AltaHijoPeticion(
        @NotBlank @Size(max = 40) String nombre,
        @NotBlank @Size(max = 40) String avatar,
        @Min(2005) @Max(2022) Integer anioNacimiento,
        @Pattern(regexp = "^[0-9]{4,6}$", message = "el PIN debe tener de 4 a 6 cifras") String pin,
        @NotNull @AssertTrue(message = "hace falta el consentimiento del adulto") Boolean consentimiento) {
}
