package es.upm.tfg.familia;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Esquema {@code FamiliaCambios}: solo se cambian los campos que vienen. */
public record FamiliaCambios(
        @Size(min = 1, max = 60) String nombre,
        @Size(min = 1, max = 30) String nombreMoneda,
        @Min(1) Integer monedasPorEuro) {

    boolean vacio() {
        return nombre == null && nombreMoneda == null && monedasPorEuro == null;
    }
}
