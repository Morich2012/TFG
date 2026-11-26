package es.upm.tfg.cuentas;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Esquema {@code Cuenta}. {@code saldoEuros} es solo informativo: se calcula con el tipo de cambio
 * de la familia y nunca se usa para operar (RNF-07). Los campos de hucha llegan en la iteración 3.
 */
public record CuentaDto(long id, TipoCuenta tipo, String nombre, long saldo, Long titularId, String icono,
        Long meta, LocalDate fechaObjetivo, LocalDate bloqueadaHasta, Long bonificacionPrometida,
        BigDecimal progresoMeta, long interesGenerado, BigDecimal saldoEuros) {
}
