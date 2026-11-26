package es.upm.tfg.cuentas;

import java.time.OffsetDateTime;

/**
 * Esquema {@code Movimiento}. {@code sentido} y {@code saldoTras} solo vienen en el historial de
 * una cuenta, porque dependen de qué cuenta se consulta.
 */
public record MovimientoDto(long id, TipoMovimiento tipo, long cantidad, String concepto, OffsetDateTime creadoEn,
        long cuentaOrigenId, long cuentaDestinoId, Long autorId, CategoriaDto categoria, Long corrigeAId,
        Sentido sentido, Long saldoTras) {

    public enum Sentido {
        ENTRADA,
        SALIDA
    }
}
