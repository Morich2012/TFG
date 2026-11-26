package es.upm.tfg.cuentas;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.upm.tfg.comun.CodigoError;
import es.upm.tfg.comun.ErrorApi;
import es.upm.tfg.seguridad.UsuarioActual;

/**
 * Apertura de cuentas y operaciones de los adultos sobre el libro: ajustes manuales (RF-12) y
 * correcciones (RNF-09). Las huchas llegan en la iteración 3.
 */
@Service
@Transactional
public class ServicioCuentas {

    private final CuentaRepositorio cuentas;
    private final MovimientoRepositorio movimientos;
    private final ServicioLibro libro;
    private final ConsultasCuentas consultas;

    public ServicioCuentas(CuentaRepositorio cuentas, MovimientoRepositorio movimientos, ServicioLibro libro,
            ConsultasCuentas consultas) {
        this.cuentas = cuentas;
        this.movimientos = movimientos;
        this.libro = libro;
        this.consultas = consultas;
    }

    public Cuenta abrirBanco(long familiaId) {
        return cuentas.save(new Cuenta(familiaId, TipoCuenta.BANCO, null, "Banco familiar"));
    }

    public Cuenta abrirMonedero(long familiaId, long titularId, String nombre) {
        return cuentas.save(new Cuenta(familiaId, TipoCuenta.MONEDERO, titularId, nombre));
    }

    /**
     * Ingreso (cantidad positiva: banco → monedero) o retirada (negativa: monedero → banco)
     * justificada en el monedero de un hijo (RF-12). Si la app repite la petición con la misma
     * {@code Idempotency-Key}, devuelve el movimiento ya hecho sin cobrar dos veces.
     */
    public MovimientoDto ajustar(UsuarioActual u, AjustePeticion p, String clave) {
        var repetido = libro.porClave(u.familiaId(), clave);
        if (repetido.isPresent()) {
            return consultas.movimiento(u.familiaId(), repetido.get().getId());
        }
        long monedero = libro.monedero(u.familiaId(), p.hijoId())
                .orElseThrow(() -> ErrorApi.datosInvalidos("El hijo no existe en esta familia"));
        long banco = libro.banco(u.familiaId());
        boolean ingreso = p.cantidad() > 0;
        NuevoMovimiento n = NuevoMovimiento.de(u.familiaId(), ingreso ? banco : monedero, ingreso ? monedero : banco,
                Math.abs(p.cantidad()), TipoMovimiento.AJUSTE_MANUAL, p.concepto().strip(), u.miembroId())
                .conClave(clave);
        return consultas.movimiento(u.familiaId(), libro.registrar(n).getId());
    }

    /**
     * Anula un movimiento con otro igual en sentido contrario, enlazado con corrige_a_id. El
     * original no se toca y solo se corrige una vez (RNF-09). Si el hijo ya gastó ese dinero, no
     * hay saldo para devolverlo y responde SALDO_INSUFICIENTE.
     */
    public MovimientoDto corregir(UsuarioActual u, long movimientoId, CorreccionPeticion p) {
        Movimiento original = movimientos.findByIdAndFamiliaId(movimientoId, u.familiaId())
                .orElseThrow(ErrorApi::noEncontrado);
        if (original.getTipo() == TipoMovimiento.CORRECCION) {
            throw ErrorApi.estadoInvalido("Una corrección no se puede corregir");
        }
        if (movimientos.existsByCorrigeAId(original.getId())) {
            throw new ErrorApi(CodigoError.YA_CORREGIDO);
        }
        NuevoMovimiento n = NuevoMovimiento.de(u.familiaId(), original.getCuentaDestinoId(),
                original.getCuentaOrigenId(), original.getCantidad(), TipoMovimiento.CORRECCION,
                p.concepto().strip(), u.miembroId()).corrigiendo(original.getId());
        return consultas.movimiento(u.familiaId(), libro.registrar(n).getId());
    }
}
