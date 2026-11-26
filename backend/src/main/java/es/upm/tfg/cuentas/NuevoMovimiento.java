package es.upm.tfg.cuentas;

/**
 * Datos para registrar un movimiento con {@link ServicioLibro#registrar}. {@code origenTipo} y
 * {@code origenId} enlazan el apunte con la operación que lo causó (por ejemplo, la asignación de
 * una tarea); {@code claveIdempotencia} es la cabecera {@code Idempotency-Key} de la petición.
 */
public record NuevoMovimiento(long familiaId, long cuentaOrigenId, long cuentaDestinoId, long cantidad,
        TipoMovimiento tipo, String concepto, Long autorId, Long categoriaId, String origenTipo, Long origenId,
        Long corrigeAId, String claveIdempotencia) {

    public static NuevoMovimiento de(long familiaId, long origen, long destino, long cantidad, TipoMovimiento tipo,
            String concepto, Long autorId) {
        return new NuevoMovimiento(familiaId, origen, destino, cantidad, tipo, concepto, autorId, null, null, null,
                null, null);
    }

    public NuevoMovimiento originadoPor(String tipoOperacion, long idOperacion) {
        return new NuevoMovimiento(familiaId, cuentaOrigenId, cuentaDestinoId, cantidad, tipo, concepto, autorId,
                categoriaId, tipoOperacion, idOperacion, corrigeAId, claveIdempotencia);
    }

    public NuevoMovimiento corrigiendo(long movimientoId) {
        return new NuevoMovimiento(familiaId, cuentaOrigenId, cuentaDestinoId, cantidad, tipo, concepto, autorId,
                categoriaId, origenTipo, origenId, movimientoId, claveIdempotencia);
    }

    public NuevoMovimiento conClave(String clave) {
        return new NuevoMovimiento(familiaId, cuentaOrigenId, cuentaDestinoId, cantidad, tipo, concepto, autorId,
                categoriaId, origenTipo, origenId, corrigeAId, clave);
    }
}
