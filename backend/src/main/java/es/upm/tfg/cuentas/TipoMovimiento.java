package es.upm.tfg.cuentas;

/** Tipos de movimiento del libro. El disparador movimiento_saldos comprueba el sentido de cada uno. */
public enum TipoMovimiento {
    RECOMPENSA_TAREA,
    PAGA,
    COMPRA,
    DEVOLUCION_COMPRA,
    AHORRO,
    RETIRADA_HUCHA,
    INTERES,
    BONIFICACION_BLOQUEO,
    CANJE_EUROS,
    TRANSFERENCIA,
    AJUSTE_MANUAL,
    CORRECCION
}
