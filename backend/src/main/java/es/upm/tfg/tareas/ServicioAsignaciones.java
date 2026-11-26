package es.upm.tfg.tareas;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.upm.tfg.comun.ErrorApi;
import es.upm.tfg.cuentas.Movimiento;
import es.upm.tfg.cuentas.NuevoMovimiento;
import es.upm.tfg.cuentas.ServicioLibro;
import es.upm.tfg.cuentas.TipoMovimiento;
import es.upm.tfg.familia.CalendarioFamilia;
import es.upm.tfg.seguridad.UsuarioActual;

/**
 * Lo que cada hijo tiene que hacer y su ciclo: PENDIENTE → HECHA (el hijo la entrega, RF-07) →
 * APROBADA (un adulto la aprueba y se paga, RF-08) o de vuelta a PENDIENTE si la rechaza.
 *
 * <p>Las asignaciones de las tareas diarias y semanales se crean al consultar el día de hoy
 * (en la zona horaria de la familia), no con un proceso nocturno. Una asignación pendiente caduca
 * al acabar su día, o la de una tarea única al pasar su fecha límite.</p>
 */
@Service
@Transactional
public class ServicioAsignaciones {

    /** Valor de movimiento.origen_tipo en los pagos de tareas. */
    static final String ORIGEN = "ASIGNACION_TAREA";

    private final AsignacionRepositorio asignaciones;
    private final RevisionTareaRepositorio revisiones;
    private final TareaRepositorio tareas;
    private final ServicioLibro libro;
    private final CalendarioFamilia calendario;
    private final ApplicationEventPublisher eventos;
    private final JdbcClient jdbc;

    public ServicioAsignaciones(AsignacionRepositorio asignaciones, RevisionTareaRepositorio revisiones,
            TareaRepositorio tareas, ServicioLibro libro, CalendarioFamilia calendario,
            ApplicationEventPublisher eventos, JdbcClient jdbc) {
        this.asignaciones = asignaciones;
        this.revisiones = revisiones;
        this.tareas = tareas;
        this.libro = libro;
        this.calendario = calendario;
        this.eventos = eventos;
        this.jdbc = jdbc;
    }

    /**
     * Sin {@code fecha} ni {@code estado}, devuelve las de hoy. Con {@code estado} y sin fecha, todas
     * las de ese estado (con HECHA, la bandeja del adulto). Un hijo solo recibe las suyas.
     */
    public List<AsignacionDto> listar(UsuarioActual u, Long hijoId, LocalDate fecha, EstadoAsignacion estado) {
        LocalDate hoy = calendario.hoy(u.familiaId());
        caducar(u.familiaId(), hoy);
        LocalDate dia = fecha != null ? fecha : (estado == null ? hoy : null);
        if (hoy.equals(dia)) {
            generarDelDia(u.familiaId(), hoy);
        }
        StringBuilder sql = new StringBuilder(SELECT_ASIGNACION).append(" WHERE a.familia_id = :familia");
        Map<String, Object> p = new HashMap<>();
        p.put("familia", u.familiaId());
        Long hijo = u.esAdulto() ? hijoId : Long.valueOf(u.miembroId());
        if (hijo != null) {
            sql.append(" AND a.hijo_id = :hijo");
            p.put("hijo", hijo);
        }
        if (estado != null) {
            sql.append(" AND a.estado = :estado");
            p.put("estado", estado.name());
        }
        if (dia != null) {
            // Las del día, más las tareas únicas abiertas que siguen vigentes ese día.
            sql.append(" ").append("""
                     AND (a.fecha = :dia OR (t.periodicidad = 'UNICA' AND a.fecha <= :dia
                          AND a.estado IN ('PENDIENTE', 'HECHA')
                          AND (t.fecha_limite IS NULL OR t.fecha_limite >= :dia)))""");
            p.put("dia", dia);
        }
        sql.append(" ORDER BY a.fecha, t.titulo, a.id");
        return jdbc.sql(sql.toString()).params(p).query(ServicioAsignaciones::dto).list();
    }

    /** El hijo entrega su tarea (RF-07). Solo la suya, pendiente y del día (o única aún vigente). */
    public AsignacionDto entregar(UsuarioActual u, long asignacionId) {
        Asignacion a = bloquear(u, asignacionId);
        if (a.getHijoId() != u.miembroId()) {
            throw ErrorApi.noEncontrado();
        }
        if (a.getEstado() != EstadoAsignacion.PENDIENTE) {
            throw ErrorApi.estadoInvalido("La tarea no está pendiente");
        }
        Tarea t = tareas.findById(a.getTareaId()).orElseThrow();
        LocalDate hoy = calendario.hoy(u.familiaId());
        boolean vigente = t.getPeriodicidad() == Periodicidad.UNICA
                ? t.getFechaLimite() == null || !t.getFechaLimite().isBefore(hoy)
                : a.getFecha().equals(hoy);
        if (!vigente) {
            throw ErrorApi.estadoInvalido("El plazo de esta tarea ya ha pasado");
        }
        a.entregar();
        asignaciones.saveAndFlush(a);
        eventos.publishEvent(new TareaEntregada(u.familiaId(), a.getId(), a.getHijoId()));
        return dto(u.familiaId(), a.getId());
    }

    /**
     * Un adulto aprueba o rechaza una tarea entregada (RF-08). Al aprobarla, en la misma
     * transacción se crea el movimiento banco → monedero por la recompensa pactada, la asignación
     * pasa a APROBADA y se publica {@link TareaAprobada}. Al rechazarla hace falta un comentario y
     * vuelve a PENDIENTE. Si la app repite la aprobación con la misma Idempotency-Key, devuelve la
     * asignación sin pagar otra vez.
     */
    public AsignacionDto revisar(UsuarioActual u, long asignacionId, RevisionPeticion p, String clave) {
        var repetido = libro.porClave(u.familiaId(), clave);
        if (repetido.isPresent()) {
            Movimiento m = repetido.get();
            if (!ORIGEN.equals(m.getOrigenTipo()) || m.getOrigenId() != asignacionId) {
                throw ErrorApi.estadoInvalido("Esa Idempotency-Key ya se usó en otra operación");
            }
            return dto(u.familiaId(), asignacionId);
        }
        Asignacion a = bloquear(u, asignacionId);
        if (a.getEstado() != EstadoAsignacion.HECHA) {
            throw ErrorApi.estadoInvalido("La tarea no está pendiente de revisar");
        }
        String comentario = p.comentario() == null || p.comentario().isBlank() ? null : p.comentario().strip();
        if (p.resultado() == ResultadoRevision.RECHAZADA) {
            if (comentario == null) {
                throw ErrorApi.datosInvalidos("Explica por qué rechazas la tarea");
            }
            a.rechazar();
        } else {
            long monedero = libro.monedero(u.familiaId(), a.getHijoId())
                    .orElseThrow(() -> ErrorApi.estadoInvalido("El hijo ya no está en la familia"));
            Tarea t = tareas.findById(a.getTareaId()).orElseThrow();
            Movimiento pago = libro.registrar(NuevoMovimiento.de(u.familiaId(), libro.banco(u.familiaId()), monedero,
                    a.getRecompensa(), TipoMovimiento.RECOMPENSA_TAREA, t.getTitulo(), u.miembroId())
                    .originadoPor(ORIGEN, a.getId()).conClave(clave));
            a.aprobar(pago.getId());
            eventos.publishEvent(new TareaAprobada(u.familiaId(), a.getId(), a.getHijoId(), a.getRecompensa(),
                    pago.getId()));
        }
        asignaciones.saveAndFlush(a);
        revisiones.saveAndFlush(new RevisionTarea(a.getId(), p.resultado(), comentario, u.miembroId()));
        return dto(u.familiaId(), a.getId());
    }

    /** Asignación de una tarea única para cada hijo, con fecha de hoy y la recompensa actual. */
    void crearDeTareaUnica(Tarea t, Collection<Long> hijos, LocalDate hoy) {
        for (Long hijo : hijos) {
            jdbc.sql("""
                    INSERT INTO asignacion_tarea (familia_id, tarea_id, hijo_id, fecha, recompensa)
                    VALUES (?, ?, ?, ?, ?) ON CONFLICT (tarea_id, hijo_id, fecha) DO NOTHING""")
                    .params(t.getFamiliaId(), t.getId(), hijo, hoy, t.getRecompensa()).update();
        }
    }

    /** Borra las asignaciones aún pendientes de la tarea (de esos hijos, o de todos si es null). */
    void borrarPendientes(long tareaId, Collection<Long> hijos) {
        if (hijos != null && hijos.isEmpty()) {
            return;
        }
        String sql = "DELETE FROM asignacion_tarea WHERE tarea_id = :tarea AND estado = 'PENDIENTE'"
                + (hijos == null ? "" : " AND hijo_id IN (:hijos)");
        var consulta = jdbc.sql(sql).param("tarea", tareaId);
        if (hijos != null) {
            consulta = consulta.param("hijos", hijos);
        }
        consulta.update();
    }

    /**
     * Crea las asignaciones de hoy de las tareas diarias y semanales activas. ON CONFLICT hace que
     * dos consultas simultáneas no creen la misma dos veces (asignacion_dia_uq).
     */
    private void generarDelDia(long familiaId, LocalDate hoy) {
        jdbc.sql("""
                INSERT INTO asignacion_tarea (familia_id, tarea_id, hijo_id, fecha, recompensa)
                SELECT t.familia_id, t.id, th.hijo_id, :hoy, t.recompensa
                  FROM tarea t
                  JOIN tarea_hijo th ON th.tarea_id = t.id
                  JOIN miembro m ON m.id = th.hijo_id
                 WHERE t.familia_id = :familia AND t.archivada_en IS NULL AND m.dado_de_baja_en IS NULL
                   AND (t.periodicidad = 'DIARIA'
                        OR (t.periodicidad = 'SEMANAL' AND (t.dias_semana & :bit) <> 0))
                ON CONFLICT (tarea_id, hijo_id, fecha) DO NOTHING""")
                .param("hoy", hoy).param("familia", familiaId)
                .param("bit", DiaSemana.de(hoy.getDayOfWeek()).bit()).update();
    }

    private void caducar(long familiaId, LocalDate hoy) {
        jdbc.sql("""
                UPDATE asignacion_tarea a SET estado = 'CADUCADA', version = a.version + 1
                  FROM tarea t
                 WHERE t.id = a.tarea_id AND a.familia_id = :familia AND a.estado = 'PENDIENTE'
                   AND ((t.periodicidad <> 'UNICA' AND a.fecha < :hoy)
                        OR (t.periodicidad = 'UNICA' AND t.fecha_limite < :hoy))""")
                .param("familia", familiaId).param("hoy", hoy).update();
    }

    private Asignacion bloquear(UsuarioActual u, long asignacionId) {
        return asignaciones.findWithLockByIdAndFamiliaId(asignacionId, u.familiaId())
                .orElseThrow(ErrorApi::noEncontrado);
    }

    private AsignacionDto dto(long familiaId, long asignacionId) {
        return jdbc.sql(SELECT_ASIGNACION + " WHERE a.familia_id = ? AND a.id = ?")
                .params(familiaId, asignacionId).query(ServicioAsignaciones::dto).single();
    }

    private static final String SELECT_ASIGNACION = """
            SELECT a.id, a.tarea_id, a.hijo_id, a.fecha, a.estado, a.recompensa, a.hecha_en, a.movimiento_id,
                   t.titulo, t.icono,
                   (SELECT r.comentario FROM revision_tarea r WHERE r.asignacion_id = a.id
                     ORDER BY r.revisada_en DESC, r.id DESC LIMIT 1) AS comentario
              FROM asignacion_tarea a
              JOIN tarea t ON t.id = a.tarea_id
              JOIN miembro h ON h.id = a.hijo_id AND h.dado_de_baja_en IS NULL
            """;

    private static AsignacionDto dto(ResultSet rs, int fila) throws SQLException {
        return new AsignacionDto(rs.getLong("id"), rs.getLong("tarea_id"), rs.getLong("hijo_id"),
                rs.getObject("fecha", LocalDate.class), EstadoAsignacion.valueOf(rs.getString("estado")),
                rs.getString("titulo"), rs.getString("icono"), rs.getLong("recompensa"),
                rs.getObject("hecha_en", OffsetDateTime.class), (Long) rs.getObject("movimiento_id"),
                rs.getString("comentario"));
    }
}
