package es.upm.tfg.cuentas;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Esquema {@code AjustePeticion} (RF-12): cantidad positiva = ingreso, negativa = retirada. */
public record AjustePeticion(
        @NotNull @Positive Long hijoId,
        @NotNull Long cantidad,
        @NotBlank @Size(min = 3, max = 140) String concepto) {

    @AssertTrue(message = "la cantidad no puede ser 0")
    public boolean isCantidadNoNula() {
        return cantidad == null || cantidad != 0;
    }
}
