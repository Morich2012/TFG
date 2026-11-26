package es.upm.tfg.cuentas;

import java.util.Optional;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.upm.tfg.comun.CodigoError;
import es.upm.tfg.comun.ErrorApi;
import es.upm.tfg.comun.RestriccionBd;

/**
 * Único punto por el que el backend mueve dinero: inserta movimientos en el libro. Los módulos
 * (tareas, tienda, paga...) nunca tocan saldos; piden un movimiento y la base de datos actualiza
 * las dos cuentas en la misma transacción (disparador movimiento_saldos).
 */
@Service
@Transactional
public class ServicioLibro {

    private final MovimientoRepositorio movimientos;
    private final JdbcClient jdbc;

    public ServicioLibro(MovimientoRepositorio movimientos, JdbcClient jdbc) {
        this.movimientos = movimientos;
        this.jdbc = jdbc;
    }

    /**
     * Registra el movimiento. Antes comprueba el saldo para dar un mensaje claro; si dos
     * operaciones llegan a la vez, la restricción saldo_no_negativo de la base de datos rechaza la
     * segunda y también se responde SALDO_INSUFICIENTE (RNF-08).
     */
    public Movimiento registrar(NuevoMovimiento n) {
        long saldo = saldoGastable(n.cuentaOrigenId());
        if (saldo < n.cantidad()) {
            throw saldoInsuficiente(n.cantidad() - saldo);
        }
        try {
            return movimientos.saveAndFlush(new Movimiento(n));
        } catch (DataIntegrityViolationException e) {
            if (RestriccionBd.es(e, "saldo_no_negativo")) {
                throw new ErrorApi(CodigoError.SALDO_INSUFICIENTE, "No hay saldo suficiente");
            }
            if (RestriccionBd.es(e, "movimiento_corrige_a_id_key")) {
                throw new ErrorApi(CodigoError.YA_CORREGIDO);
            }
            if (RestriccionBd.es(e, "movimiento_idempotencia_uq")) {
                throw ErrorApi.estadoInvalido("Esta operación ya se está procesando");
            }
            throw e;
        }
    }

    /** Movimiento ya registrado con esa cabecera Idempotency-Key, si lo hay. */
    @Transactional(readOnly = true)
    public Optional<Movimiento> porClave(long familiaId, String clave) {
        return clave == null ? Optional.empty() : movimientos.findByFamiliaIdAndClaveIdempotencia(familiaId, clave);
    }

    @Transactional(readOnly = true)
    public long banco(long familiaId) {
        return jdbc.sql("SELECT id FROM cuenta WHERE familia_id = ? AND tipo = 'BANCO'")
                .param(familiaId).query(Long.class).single();
    }

    /** Monedero abierto de un hijo activo de la familia. */
    @Transactional(readOnly = true)
    public Optional<Long> monedero(long familiaId, long hijoId) {
        return jdbc.sql("""
                SELECT c.id FROM cuenta c JOIN miembro m ON m.id = c.titular_id
                 WHERE c.familia_id = ? AND c.titular_id = ? AND c.tipo = 'MONEDERO' AND c.cerrada_en IS NULL
                   AND m.rol = 'HIJO' AND m.dado_de_baja_en IS NULL""")
                .params(familiaId, hijoId).query(Long.class).optional();
    }

    /** El banco puede quedar en negativo: es el que emite las monedas. */
    private long saldoGastable(long cuentaId) {
        return jdbc.sql("SELECT CASE WHEN tipo = 'BANCO' THEN 9223372036854775807 ELSE saldo END FROM cuenta WHERE id = ?")
                .param(cuentaId).query(Long.class).single();
    }

    static ErrorApi saldoInsuficiente(long falta) {
        return new ErrorApi(CodigoError.SALDO_INSUFICIENTE,
                "Faltan " + falta + (falta == 1 ? " moneda" : " monedas"));
    }
}
