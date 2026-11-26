package es.upm.tfg.cuentas;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.upm.tfg.comun.ErrorApi;
import es.upm.tfg.seguridad.UsuarioActual;

/**
 * Lecturas de cuentas, movimientos y categorías (RF-09, RF-10, RF-16) con SQL directo: son
 * consultas de solo lectura con sumas y ventanas que no encajan en entidades JPA.
 *
 * <p>Visibilidad: un hijo solo ve sus cuentas; un adulto ve las de todos los hijos y, si lo pide,
 * el banco de la familia (su saldo negativo es el total de monedas en circulación).</p>
 */
@Service
@Transactional(readOnly = true)
public class ConsultasCuentas {

    private static final String SELECT_CUENTA = """
            SELECT c.id, c.tipo, c.nombre, c.saldo, c.titular_id, c.icono, c.meta, c.fecha_objetivo,
                   c.bloqueada_hasta, f.monedas_por_euro,
                   COALESCE((SELECT sum(m.cantidad) FROM movimiento m
                              WHERE m.cuenta_destino_id = c.id AND m.tipo = 'INTERES'), 0) AS interes
              FROM cuenta c
              JOIN familia f ON f.id = c.familia_id
              LEFT JOIN miembro t ON t.id = c.titular_id
             WHERE c.familia_id = :familia AND c.cerrada_en IS NULL AND t.dado_de_baja_en IS NULL
            """;

    private final JdbcClient jdbc;

    public ConsultasCuentas(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<CuentaDto> listar(UsuarioActual u, Long titularId, TipoCuenta tipo) {
        StringBuilder sql = new StringBuilder(SELECT_CUENTA);
        Map<String, Object> p = new HashMap<>();
        p.put("familia", u.familiaId());
        if (!u.esAdulto()) {
            sql.append(" AND c.titular_id = :yo");
            p.put("yo", u.miembroId());
        }
        if (titularId != null) {
            sql.append(" AND c.titular_id = :titular");
            p.put("titular", titularId);
        }
        if (tipo != null) {
            sql.append(" AND c.tipo = :tipo");
            p.put("tipo", tipo.name());
        } else {
            sql.append(" AND c.tipo <> 'BANCO'");
        }
        sql.append(" ORDER BY c.titular_id NULLS FIRST, c.tipo DESC, c.id");
        return jdbc.sql(sql.toString()).params(p).query(ConsultasCuentas::cuenta).list();
    }

    /** Cuenta visible para el usuario, o 404. */
    public CuentaDto obtener(UsuarioActual u, long cuentaId) {
        String sql = SELECT_CUENTA + " AND c.id = :id" + (u.esAdulto() ? "" : " AND c.titular_id = :yo");
        var consulta = jdbc.sql(sql).param("familia", u.familiaId()).param("id", cuentaId);
        if (!u.esAdulto()) {
            consulta = consulta.param("yo", u.miembroId());
        }
        return consulta.query(ConsultasCuentas::cuenta).optional().orElseThrow(ErrorApi::noEncontrado);
    }

    /**
     * Historial de una cuenta, del más reciente al más antiguo, con el saldo que quedó tras cada
     * movimiento (suma acumulada en orden de inserción). Las fechas se filtran en la zona de la familia.
     */
    public PaginaMovimientos movimientos(UsuarioActual u, long cuentaId, LocalDate desde, LocalDate hasta,
            TipoMovimiento tipo, int pagina, int tamano) {
        obtener(u, cuentaId);
        StringBuilder filtro = new StringBuilder(" WHERE TRUE");
        Map<String, Object> p = new HashMap<>();
        p.put("cuenta", cuentaId);
        p.put("familia", u.familiaId());
        if (desde != null) {
            filtro.append(" AND s.dia >= :desde");
            p.put("desde", desde);
        }
        if (hasta != null) {
            filtro.append(" AND s.dia <= :hasta");
            p.put("hasta", hasta);
        }
        if (tipo != null) {
            filtro.append(" AND s.tipo = :tipo");
            p.put("tipo", tipo.name());
        }
        String historial = """
                WITH propios AS (
                    SELECT m.*, (m.creado_en AT TIME ZONE f.zona_horaria)::date AS dia,
                           CASE WHEN m.cuenta_destino_id = :cuenta THEN m.cantidad ELSE -m.cantidad END AS delta
                      FROM movimiento m JOIN familia f ON f.id = m.familia_id
                     WHERE m.familia_id = :familia
                       AND (m.cuenta_origen_id = :cuenta OR m.cuenta_destino_id = :cuenta)),
                conSaldo AS (
                    SELECT propios.*, sum(delta) OVER (ORDER BY creado_en, id) AS saldo_tras FROM propios)
                """;
        long total = jdbc.sql(historial + "SELECT count(*) FROM conSaldo s" + filtro).params(p)
                .query(Long.class).single();
        p.put("limite", tamano);
        p.put("salto", (long) pagina * tamano);
        List<MovimientoDto> contenido = jdbc.sql(historial + """
                SELECT s.*, cat.nombre AS cat_nombre, cat.icono AS cat_icono
                  FROM conSaldo s LEFT JOIN categoria cat ON cat.id = s.categoria_id
                """ + filtro
                + " ORDER BY s.creado_en DESC, s.id DESC LIMIT :limite OFFSET :salto")
                .params(p).query(ConsultasCuentas::movimientoConSaldo).list();
        return new PaginaMovimientos(contenido, pagina, tamano, total);
    }

    /** Un movimiento de la familia, sin datos relativos a una cuenta. */
    public MovimientoDto movimiento(long familiaId, long movimientoId) {
        return jdbc.sql("""
                SELECT m.*, cat.nombre AS cat_nombre, cat.icono AS cat_icono
                  FROM movimiento m LEFT JOIN categoria cat ON cat.id = m.categoria_id
                 WHERE m.familia_id = ? AND m.id = ?""")
                .params(familiaId, movimientoId).query((rs, i) -> movimiento(rs, null, null)).single();
    }

    /** Categorías comunes y las propias de la familia (RF-16). */
    public List<CategoriaDto> categorias(UsuarioActual u) {
        return jdbc.sql("""
                SELECT id, nombre, icono FROM categoria
                 WHERE familia_id IS NULL OR familia_id = ? ORDER BY familia_id NULLS FIRST, nombre""")
                .param(u.familiaId())
                .query((rs, i) -> new CategoriaDto(rs.getLong("id"), rs.getString("nombre"), rs.getString("icono")))
                .list();
    }

    private static CuentaDto cuenta(ResultSet rs, int fila) throws SQLException {
        long saldo = rs.getLong("saldo");
        Long meta = (Long) rs.getObject("meta");
        BigDecimal progreso = meta == null ? null
                : BigDecimal.valueOf(Math.min(saldo, meta)).divide(BigDecimal.valueOf(meta), 4, RoundingMode.DOWN);
        BigDecimal euros = BigDecimal.valueOf(saldo)
                .divide(BigDecimal.valueOf(rs.getInt("monedas_por_euro")), 2, RoundingMode.HALF_EVEN);
        return new CuentaDto(rs.getLong("id"), TipoCuenta.valueOf(rs.getString("tipo")), rs.getString("nombre"), saldo,
                (Long) rs.getObject("titular_id"), rs.getString("icono"), meta,
                rs.getObject("fecha_objetivo", LocalDate.class), rs.getObject("bloqueada_hasta", LocalDate.class), null,
                progreso, rs.getLong("interes"), euros);
    }

    private static MovimientoDto movimientoConSaldo(ResultSet rs, int fila) throws SQLException {
        MovimientoDto.Sentido sentido = rs.getLong("delta") > 0 ? MovimientoDto.Sentido.ENTRADA
                : MovimientoDto.Sentido.SALIDA;
        return movimiento(rs, sentido, rs.getLong("saldo_tras"));
    }

    private static MovimientoDto movimiento(ResultSet rs, MovimientoDto.Sentido sentido, Long saldoTras)
            throws SQLException {
        Long categoriaId = (Long) rs.getObject("categoria_id");
        CategoriaDto categoria = categoriaId == null ? null
                : new CategoriaDto(categoriaId, rs.getString("cat_nombre"), rs.getString("cat_icono"));
        return new MovimientoDto(rs.getLong("id"), TipoMovimiento.valueOf(rs.getString("tipo")),
                rs.getLong("cantidad"), rs.getString("concepto"), rs.getObject("creado_en", OffsetDateTime.class),
                rs.getLong("cuenta_origen_id"), rs.getLong("cuenta_destino_id"), (Long) rs.getObject("autor_id"),
                categoria, (Long) rs.getObject("corrige_a_id"), sentido, saldoTras);
    }
}
