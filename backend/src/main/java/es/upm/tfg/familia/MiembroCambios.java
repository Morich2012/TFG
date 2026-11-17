package es.upm.tfg.familia;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Esquema {@code MiembroCambios} (RF-05). Año de nacimiento y PIN solo valen para hijos. */
public record MiembroCambios(
        @Size(min = 1, max = 40) String nombre,
        @Size(min = 1, max = 40) String avatar,
        @Min(2005) @Max(2022) Integer anioNacimiento,
        @Pattern(regexp = "^[0-9]{4,6}$", message = "el PIN debe tener de 4 a 6 cifras") String pin) {

    boolean vacio() {
        return nombre == null && avatar == null && anioNacimiento == null && pin == null;
    }
}
