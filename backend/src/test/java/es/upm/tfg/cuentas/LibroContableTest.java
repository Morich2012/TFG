package es.upm.tfg.cuentas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

import es.upm.tfg.PruebaIntegracion;

/**
 * Reglas del dinero que garantiza la base de datos (V1), traídas de
 * docs/diseno/pruebas/prueba-libro.sql. Las cuentas se crean por la API para probar también que
 * el registro y el alta abren el banco y el monedero.
 */
class LibroContableTest extends PruebaIntegracion {

    @Autowired
    JdbcClient jdbc;

    private long familia;
    private long banco;
    private long monedero;

    private void prepararFamiliaConHijo() throws Exception {
        Sesion adulto = registrarFamilia();
        long hijo = altaHijo(adulto, "Lucía", 2017, null);
        familia = jdbc.sql("SELECT familia_id FROM miembro WHERE id = ?").param(hijo).query(Long.class).single();
        banco = jdbc.sql("SELECT id FROM cuenta WHERE familia_id = ? AND tipo = 'BANCO'").param(familia)
                .query(Long.class).single();
        monedero = jdbc.sql("SELECT id FROM cuenta WHERE titular_id = ? AND tipo = 'MONEDERO'").param(hijo)
                .query(Long.class).single();
    }

    private void mover(long origen, long destino, long cantidad, String tipo) {
        jdbc.sql("""
                INSERT INTO movimiento (familia_id, cuenta_origen_id, cuenta_destino_id, cantidad, tipo, concepto)
                VALUES (?, ?, ?, ?, ?, 'prueba')""")
                .params(familia, origen, destino, cantidad, tipo).update();
    }

    private long saldo(long cuenta) {
        return jdbc.sql("SELECT saldo FROM cuenta WHERE id = ?").param(cuenta).query(Long.class).single();
    }

    @Test
    void cadaMovimientoActualizaLosDosSaldosYLaFamiliaSumaCero() throws Exception {
        prepararFamiliaConHijo();
        mover(banco, monedero, 30, "RECOMPENSA_TAREA");
        mover(monedero, banco, 12, "COMPRA");

        assertThat(saldo(monedero)).isEqualTo(18);
        assertThat(saldo(banco)).isEqualTo(-18);
        Long descuadres = jdbc.sql("SELECT count(*) FROM cuenta_cuadre WHERE saldo_cacheado <> saldo_libro")
                .query(Long.class).single();
        assertThat(descuadres).isZero();
    }

    @Test
    void unMonederoNoPuedeQuedarEnNegativo() throws Exception {
        prepararFamiliaConHijo();
        mover(banco, monedero, 10, "PAGA");
        assertThatThrownBy(() -> mover(monedero, banco, 11, "COMPRA")).isInstanceOf(DataAccessException.class);
        assertThat(saldo(monedero)).isEqualTo(10);
    }

    @Test
    void losMovimientosNoSePuedenModificarNiBorrar() throws Exception {
        prepararFamiliaConHijo();
        mover(banco, monedero, 10, "PAGA");
        assertThatThrownBy(() -> jdbc.sql("UPDATE movimiento SET cantidad = 1000 WHERE cuenta_destino_id = ?")
                .param(monedero).update()).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.sql("DELETE FROM movimiento WHERE cuenta_destino_id = ?")
                .param(monedero).update()).isInstanceOf(DataAccessException.class);
    }
}
