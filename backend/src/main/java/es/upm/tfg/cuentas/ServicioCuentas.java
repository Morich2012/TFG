package es.upm.tfg.cuentas;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Apertura de cuentas. En la iteración 1 solo se abren el banco de la familia y el monedero de
 * cada hijo; los movimientos (tareas, compras, ahorro) llegan en las siguientes iteraciones.
 */
@Service
@Transactional
public class ServicioCuentas {

    private final CuentaRepositorio cuentas;

    public ServicioCuentas(CuentaRepositorio cuentas) {
        this.cuentas = cuentas;
    }

    public Cuenta abrirBanco(long familiaId) {
        return cuentas.save(new Cuenta(familiaId, TipoCuenta.BANCO, null, "Banco familiar"));
    }

    public Cuenta abrirMonedero(long familiaId, long titularId, String nombre) {
        return cuentas.save(new Cuenta(familiaId, TipoCuenta.MONEDERO, titularId, nombre));
    }
}
