package es.upm.tfg.familia;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.upm.tfg.seguridad.UsuarioActual;

/**
 * Resumen de cada hijo para el adulto (RF-27), leído directamente del libro y de las asignaciones.
 * "Esta semana" empieza el lunes en la zona horaria de la familia.
 *
 * <p>{@code comprasPorEntregar} y {@code totalDebidoCentimos} valen 0 hasta la iteración 3, que
 * crea las tablas compra y pago_pendiente.</p>
 */
@Service
@Transactional(readOnly = true)
public class ServicioResumen {

    private final ServicioFamilia familia;
    private final CalendarioFamilia calendario;
    private final JdbcClient jdbc;

    public ServicioResumen(ServicioFamilia familia, CalendarioFamilia calendario, JdbcClient jdbc) {
        this.familia = familia;
        this.calendario = calendario;
        this.jdbc = jdbc;
    }

    public ResumenFamilia resumen(UsuarioActual u) {
        int porRevisar = jdbc.sql("""
                SELECT count(*) FROM asignacion_tarea a JOIN miembro h ON h.id = a.hijo_id
                 WHERE a.familia_id = ? AND a.estado = 'HECHA' AND h.dado_de_baja_en IS NULL""")
                .param(u.familiaId()).query(Integer.class).single();
        LocalDate lunes = calendario.inicioSemana(u.familiaId());
        List<ResumenFamilia.Hijo> hijos = familia.listar(u).stream()
                .filter(m -> m.rol() == Rol.HIJO)
                .map(m -> hijo(u.familiaId(), m, lunes))
                .toList();
        return new ResumenFamilia(porRevisar, 0, 0, hijos);
    }

    /**
     * Gastado: compras y canjes que salen del monedero, menos devoluciones. Ahorrado: lo que entra
     * en sus huchas desde el monedero o con la paga (sin intereses ni bonificaciones).
     */
    private ResumenFamilia.Hijo hijo(long familiaId, MiembroDto m, LocalDate lunes) {
        return jdbc.sql("""
                WITH cuentas AS (
                    SELECT id, tipo, saldo FROM cuenta
                     WHERE familia_id = :familia AND titular_id = :hijo AND cerrada_en IS NULL),
                semana AS (
                    SELECT mv.* FROM movimiento mv JOIN familia f ON f.id = mv.familia_id
                     WHERE mv.familia_id = :familia AND (mv.creado_en AT TIME ZONE f.zona_horaria)::date >= :lunes)
                SELECT
                  COALESCE((SELECT sum(saldo) FROM cuentas WHERE tipo = 'MONEDERO'), 0) AS monedero,
                  COALESCE((SELECT sum(saldo) FROM cuentas WHERE tipo = 'HUCHA'), 0) AS huchas,
                  GREATEST(0,
                    COALESCE((SELECT sum(cantidad) FROM semana WHERE tipo IN ('COMPRA', 'CANJE_EUROS')
                               AND cuenta_origen_id IN (SELECT id FROM cuentas WHERE tipo = 'MONEDERO')), 0)
                  - COALESCE((SELECT sum(cantidad) FROM semana WHERE tipo = 'DEVOLUCION_COMPRA'
                               AND cuenta_destino_id IN (SELECT id FROM cuentas WHERE tipo = 'MONEDERO')), 0))
                    AS gastado,
                  COALESCE((SELECT sum(cantidad) FROM semana WHERE tipo IN ('AHORRO', 'PAGA')
                             AND cuenta_destino_id IN (SELECT id FROM cuentas WHERE tipo = 'HUCHA')), 0) AS ahorrado
                """)
                .param("familia", familiaId).param("hijo", m.id()).param("lunes", lunes)
                .query((rs, i) -> new ResumenFamilia.Hijo(m, rs.getLong("monedero"), rs.getLong("huchas"),
                        rs.getLong("gastado"), rs.getLong("ahorrado")))
                .single();
    }
}
