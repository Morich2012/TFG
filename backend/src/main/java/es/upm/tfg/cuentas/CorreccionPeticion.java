package es.upm.tfg.cuentas;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuerpo de {@code PUT /movimientos/{movimientoId}/correccion}. */
public record CorreccionPeticion(@NotBlank @Size(max = 140) String concepto) {
}
