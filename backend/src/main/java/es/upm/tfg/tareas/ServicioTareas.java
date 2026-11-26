package es.upm.tfg.tareas;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import es.upm.tfg.comun.ErrorApi;
import es.upm.tfg.familia.CalendarioFamilia;
import es.upm.tfg.seguridad.UsuarioActual;

/** Definición de tareas (RF-06) y plantillas (RF-37). */
@Service
@Transactional
public class ServicioTareas {

    private static final String ICONO_POR_DEFECTO = "tarea";

    private final TareaRepositorio tareas;
    private final ServicioAsignaciones asignaciones;
    private final CalendarioFamilia calendario;
    private final JdbcClient jdbc;

    public ServicioTareas(TareaRepositorio tareas, ServicioAsignaciones asignaciones, CalendarioFamilia calendario,
            JdbcClient jdbc) {
        this.tareas = tareas;
        this.asignaciones = asignaciones;
        this.calendario = calendario;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<PlantillaTareaDto> plantillas() {
        return jdbc.sql("SELECT id, titulo, grupo, icono, recompensa_sugerida FROM plantilla_tarea ORDER BY grupo, id")
                .query((rs, i) -> new PlantillaTareaDto(rs.getLong("id"), rs.getString("titulo"),
                        rs.getString("grupo"), rs.getString("icono"), rs.getLong("recompensa_sugerida")))
                .list();
    }

    @Transactional(readOnly = true)
    public List<TareaDto> listar(UsuarioActual u, boolean incluirArchivadas) {
        List<Tarea> lista = incluirArchivadas ? tareas.findByFamiliaIdOrderById(u.familiaId())
                : tareas.findByFamiliaIdAndArchivadaEnIsNullOrderById(u.familiaId());
        return lista.stream().map(TareaDto::de).toList();
    }

    /** Crea la tarea. Si es única, crea ya la asignación de cada hijo con fecha de hoy. */
    public TareaDto crear(UsuarioActual u, TareaPeticion p) {
        LocalDate hoy = calendario.hoy(u.familiaId());
        comprobarCalendario(p.periodicidad(), p.diasSemana(), p.fechaLimite(), hoy);
        comprobarHijos(u, p.hijos());
        Tarea t = new Tarea(u.familiaId(), p.periodicidad(), u.miembroId());
        t.setTitulo(p.titulo().strip());
        t.setDescripcion(p.descripcion());
        t.setRecompensa(p.recompensa());
        t.setDiasSemana(DiaSemana.mascara(p.diasSemana()));
        t.setFechaLimite(p.fechaLimite());
        t.setIcono(p.icono() != null ? p.icono() : iconoDePlantilla(p.plantillaId()));
        t.setHijos(p.hijos());
        t = tareas.saveAndFlush(t);
        if (t.getPeriodicidad() == Periodicidad.UNICA) {
            asignaciones.crearDeTareaUnica(t, t.getHijos(), hoy);
        }
        return TareaDto.de(t);
    }

    /**
     * Cambia solo lo que viene. A un hijo que se quita se le borran las asignaciones aún
     * pendientes de esta tarea; a uno que se añade a una tarea única se le crea la suya.
     */
    public TareaDto editar(UsuarioActual u, long tareaId, TareaCambios c) {
        if (c.vacio()) {
            throw ErrorApi.datosInvalidos("No hay ningún cambio");
        }
        Tarea t = tarea(u, tareaId);
        if (t.estaArchivada()) {
            throw ErrorApi.estadoInvalido("La tarea está archivada");
        }
        if (c.periodicidad() != null && c.periodicidad() != t.getPeriodicidad()) {
            throw ErrorApi.datosInvalidos("La periodicidad no se puede cambiar; crea una tarea nueva");
        }
        LocalDate hoy = calendario.hoy(u.familiaId());
        Set<DiaSemana> dias = c.diasSemana() != null ? c.diasSemana() : DiaSemana.deMascara(t.getDiasSemana());
        LocalDate limite = c.fechaLimite() != null ? c.fechaLimite() : t.getFechaLimite();
        comprobarCalendario(t.getPeriodicidad(), dias, c.fechaLimite() != null ? limite : null, hoy);
        if (c.titulo() != null) {
            t.setTitulo(c.titulo().strip());
        }
        if (c.descripcion() != null) {
            t.setDescripcion(c.descripcion());
        }
        if (c.recompensa() != null) {
            t.setRecompensa(c.recompensa());
        }
        if (c.diasSemana() != null) {
            t.setDiasSemana(DiaSemana.mascara(dias));
        }
        if (c.fechaLimite() != null) {
            t.setFechaLimite(limite);
        }
        if (c.icono() != null) {
            t.setIcono(c.icono());
        }
        if (c.hijos() != null) {
            comprobarHijos(u, c.hijos());
            Set<Long> quitados = new HashSet<>(t.getHijos());
            quitados.removeAll(c.hijos());
            Set<Long> nuevos = new HashSet<>(c.hijos());
            nuevos.removeAll(t.getHijos());
            t.setHijos(c.hijos());
            asignaciones.borrarPendientes(t.getId(), quitados);
            if (t.getPeriodicidad() == Periodicidad.UNICA) {
                asignaciones.crearDeTareaUnica(t, nuevos, hoy);
            }
        }
        return TareaDto.de(tareas.saveAndFlush(t));
    }

    /**
     * Archiva la tarea: deja de generar asignaciones y se borran las pendientes. Las entregadas
     * siguen esperando revisión y las cobradas se conservan, por eso no se borra la tarea.
     */
    public void archivar(UsuarioActual u, long tareaId) {
        Tarea t = tarea(u, tareaId);
        t.archivar();
        asignaciones.borrarPendientes(t.getId(), null);
    }

    private Tarea tarea(UsuarioActual u, long tareaId) {
        return tareas.findByIdAndFamiliaId(tareaId, u.familiaId()).orElseThrow(ErrorApi::noEncontrado);
    }

    private static void comprobarCalendario(Periodicidad periodicidad, Set<DiaSemana> dias, LocalDate fechaLimite,
            LocalDate hoy) {
        boolean conDias = dias != null && !dias.isEmpty();
        if (periodicidad == Periodicidad.SEMANAL && !conDias) {
            throw ErrorApi.datosInvalidos("Una tarea semanal necesita al menos un día");
        }
        if (periodicidad != Periodicidad.SEMANAL && conDias) {
            throw ErrorApi.datosInvalidos("Los días de la semana solo son para las tareas semanales");
        }
        if (fechaLimite != null && periodicidad != Periodicidad.UNICA) {
            throw ErrorApi.datosInvalidos("La fecha límite solo es para las tareas únicas");
        }
        if (fechaLimite != null && fechaLimite.isBefore(hoy)) {
            throw ErrorApi.datosInvalidos("La fecha límite ya ha pasado");
        }
    }

    /** Todos los hijos indicados son hijos activos de la familia. */
    private void comprobarHijos(UsuarioActual u, Set<Long> hijos) {
        long validos = jdbc.sql("""
                SELECT count(*) FROM miembro
                 WHERE familia_id = :familia AND id IN (:ids) AND rol = 'HIJO' AND dado_de_baja_en IS NULL""")
                .param("familia", u.familiaId()).param("ids", hijos).query(Long.class).single();
        if (validos != hijos.size()) {
            throw ErrorApi.datosInvalidos("Alguno de los hijos no existe en esta familia");
        }
    }

    private String iconoDePlantilla(Long plantillaId) {
        if (plantillaId == null) {
            return ICONO_POR_DEFECTO;
        }
        return jdbc.sql("SELECT icono FROM plantilla_tarea WHERE id = ?").param(plantillaId)
                .query(String.class).optional()
                .orElseThrow(() -> ErrorApi.datosInvalidos("La plantilla no existe"));
    }
}
