package es.upm.tfg.tareas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import es.upm.tfg.PruebaIntegracion;

/** Ciclo de una tarea por la API: crear, asignar, entregar, aprobar o rechazar (RF-06..08, RF-27, RF-37). */
class TareasTest extends PruebaIntegracion {

    @Autowired
    JdbcClient jdbc;

    private static LocalDate hoy() {
        return LocalDate.now(ZoneId.of("Europe/Madrid"));
    }

    private static String diaDeHoy() {
        return DiaSemana.de(hoy().getDayOfWeek()).name();
    }

    private long crearTarea(Sesion adulto, String cuerpo) throws Exception {
        String res = mvc.perform(con(adulto, json(post(fam(adulto) + "/tareas"), cuerpo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    private long tareaDiaria(Sesion adulto, long hijo, long recompensa) throws Exception {
        return crearTarea(adulto, """
                {"titulo":"Hacer la cama","recompensa":%d,"periodicidad":"DIARIA","hijos":[%d]}"""
                .formatted(recompensa, hijo));
    }

    /** Id de la primera asignación de hoy que ve esa sesión. */
    private long asignacionDeHoy(Sesion s) throws Exception {
        String res = mvc.perform(con(s, get(fam(s) + "/asignaciones")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$[0].id")).longValue();
    }

    private ResultActions entregar(Sesion hijo, long asignacion) throws Exception {
        return mvc.perform(con(hijo, post(fam(hijo) + "/asignaciones/{id}/entregas", asignacion)));
    }

    private ResultActions revisar(Sesion adulto, long asignacion, String cuerpo, String clave) throws Exception {
        var peticion = json(post(fam(adulto) + "/asignaciones/{id}/revisiones", asignacion), cuerpo);
        if (clave != null) {
            peticion = peticion.header("Idempotency-Key", clave);
        }
        return mvc.perform(con(adulto, peticion));
    }

    private long saldoMonedero(long hijo) {
        return jdbc.sql("SELECT saldo FROM cuenta WHERE titular_id = ? AND tipo = 'MONEDERO'").param(hijo)
                .query(Long.class).single();
    }

    @Test
    void lasPlantillasSonSoloParaLosAdultos() throws Exception {
        Sesion adulto = registrarFamilia();
        mvc.perform(con(adulto, get("/api/v1/plantillas-tarea")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].recompensaSugerida").isNumber());
        long hijo = altaHijo(adulto, "Lucía", 2016, null);
        mvc.perform(con(vincularHijo(adulto, hijo), get("/api/v1/plantillas-tarea")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unaTareaAprobadaPagaLaRecompensaPactadaUnaSolaVez() throws Exception {
        Sesion adulto = registrarFamilia();
        long hijo = altaHijo(adulto, "Lucía", 2016, null);
        Sesion lucia = vincularHijo(adulto, hijo);
        tareaDiaria(adulto, hijo, 15);

        // La asignación de hoy se crea al consultar el día, y solo una vez.
        mvc.perform(con(lucia, get(fam(lucia) + "/asignaciones"))).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(con(lucia, get(fam(lucia) + "/asignaciones")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$[0].titulo").value("Hacer la cama"))
                .andExpect(jsonPath("$[0].recompensa").value(15))
                .andExpect(jsonPath("$[0].fecha").value(hoy().toString()));
        long asignacion = asignacionDeHoy(lucia);

        entregar(lucia, asignacion).andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("HECHA"))
                .andExpect(jsonPath("$.hechaEn").isString());
        entregar(lucia, asignacion).andExpect(status().isConflict());

        // Bandeja del adulto y resumen.
        mvc.perform(con(adulto, get(fam(adulto) + "/asignaciones").param("estado", "HECHA")))
                .andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(con(adulto, get(fam(adulto) + "/resumen")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tareasPorRevisar").value(1));

        revisar(adulto, asignacion, "{\"resultado\":\"APROBADA\"}", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("APROBADA"))
                .andExpect(jsonPath("$.movimientoId").isNumber());
        revisar(adulto, asignacion, "{\"resultado\":\"APROBADA\"}", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
        assertThat(saldoMonedero(hijo)).isEqualTo(15);

        mvc.perform(con(adulto, get(fam(adulto) + "/resumen")))
                .andExpect(jsonPath("$.tareasPorRevisar").value(0))
                .andExpect(jsonPath("$.comprasPorEntregar").value(0))
                .andExpect(jsonPath("$.hijos", hasSize(1)))
                .andExpect(jsonPath("$.hijos[0].miembro.nombre").value("Lucía"))
                .andExpect(jsonPath("$.hijos[0].saldoMonedero").value(15))
                .andExpect(jsonPath("$.hijos[0].gastadoEstaSemana").value(0));
        Long pagos = jdbc.sql("""
                SELECT count(*) FROM movimiento WHERE tipo = 'RECOMPENSA_TAREA'
                   AND origen_tipo = 'ASIGNACION_TAREA' AND origen_id = ?""")
                .param(asignacion).query(Long.class).single();
        assertThat(pagos).isEqualTo(1);
    }

    @Test
    void unaAprobacionRepetidaConLaMismaClaveNoPagaDosVeces() throws Exception {
        Sesion adulto = registrarFamilia();
        long hijo = altaHijo(adulto, "Lucía", 2016, null);
        Sesion lucia = vincularHijo(adulto, hijo);
        tareaDiaria(adulto, hijo, 10);
        long asignacion = asignacionDeHoy(lucia);
        entregar(lucia, asignacion).andExpect(status().isCreated());

        revisar(adulto, asignacion, "{\"resultado\":\"APROBADA\"}", "app-uuid-1").andExpect(status().isCreated());
        revisar(adulto, asignacion, "{\"resultado\":\"APROBADA\"}", "app-uuid-1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("APROBADA"));
        assertThat(saldoMonedero(hijo)).isEqualTo(10);
    }

    @Test
    void unaTareaRechazadaVuelveAPendienteConElMotivo() throws Exception {
        Sesion adulto = registrarFamilia();
        long hijo = altaHijo(adulto, "Lucía", 2016, null);
        Sesion lucia = vincularHijo(adulto, hijo);
        tareaDiaria(adulto, hijo, 10);
        long asignacion = asignacionDeHoy(lucia);
        entregar(lucia, asignacion).andExpect(status().isCreated());

        revisar(adulto, asignacion, "{\"resultado\":\"RECHAZADA\"}", null)
                .andExpect(status().isBadRequest());
        revisar(adulto, asignacion, "{\"resultado\":\"RECHAZADA\",\"comentario\":\"Falta estirar la sábana\"}", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.comentario").value("Falta estirar la sábana"));
        mvc.perform(con(lucia, get(fam(lucia) + "/asignaciones")))
                .andExpect(jsonPath("$[0].comentario").value("Falta estirar la sábana"));

        entregar(lucia, asignacion).andExpect(status().isCreated());
        revisar(adulto, asignacion, "{\"resultado\":\"APROBADA\"}", null).andExpect(status().isCreated());
        Long revisiones = jdbc.sql("SELECT count(*) FROM revision_tarea WHERE asignacion_id = ?").param(asignacion)
                .query(Long.class).single();
        assertThat(revisiones).isEqualTo(2);
        assertThat(saldoMonedero(hijo)).isEqualTo(10);
    }

    @Test
    void unHijoSoloVeYEntregaSusTareas() throws Exception {
        Sesion adulto = registrarFamilia();
        long lucia = altaHijo(adulto, "Lucía", 2016, null);
        long pablo = altaHijo(adulto, "Pablo", 2018, null);
        tareaDiaria(adulto, lucia, 10);
        tareaDiaria(adulto, pablo, 10);
        Sesion sesionLucia = vincularHijo(adulto, lucia);
        Sesion sesionPablo = vincularHijo(adulto, pablo);

        mvc.perform(con(sesionLucia, get(fam(adulto) + "/asignaciones").param("hijoId", Long.toString(pablo))))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].hijoId").value(lucia));
        entregar(sesionPablo, asignacionDeHoy(sesionLucia)).andExpect(status().isNotFound());
        mvc.perform(con(adulto, get(fam(adulto) + "/asignaciones"))).andExpect(jsonPath("$", hasSize(2)));
        revisar(sesionLucia, asignacionDeHoy(sesionLucia), "{\"resultado\":\"APROBADA\"}", null)
                .andExpect(status().isForbidden());
    }

    @Test
    void unaTareaSemanalSoloSaleLosDiasElegidos() throws Exception {
        Sesion adulto = registrarFamilia();
        long hijo = altaHijo(adulto, "Lucía", 2016, null);
        String otroDia = DiaSemana.de(hoy().getDayOfWeek().plus(1)).name();
        crearTarea(adulto, """
                {"titulo":"Sacar la basura","recompensa":5,"periodicidad":"SEMANAL","diasSemana":["%s"],"hijos":[%d]}"""
                .formatted(diaDeHoy(), hijo));
        crearTarea(adulto, """
                {"titulo":"Regar","recompensa":5,"periodicidad":"SEMANAL","diasSemana":["%s"],"hijos":[%d]}"""
                .formatted(otroDia, hijo));
        mvc.perform(con(adulto, get(fam(adulto) + "/asignaciones")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].titulo").value("Sacar la basura"));
        mvc.perform(con(adulto, get(fam(adulto) + "/tareas")))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].diasSemana[0]").value(diaDeHoy()));
    }

    @Test
    void unaTareaUnicaSeAsignaAlCrearlaYSigueAbiertaHastaSuFechaLimite() throws Exception {
        Sesion adulto = registrarFamilia();
        long hijo = altaHijo(adulto, "Lucía", 2016, null);
        Sesion lucia = vincularHijo(adulto, hijo);
        crearTarea(adulto, """
                {"titulo":"Ordenar el trastero","recompensa":30,"periodicidad":"UNICA","fechaLimite":"%s","hijos":[%d],
                 "plantillaId":2}""".formatted(hoy().plusDays(3), hijo));
        Long filas = jdbc.sql("SELECT count(*) FROM asignacion_tarea WHERE hijo_id = ?").param(hijo)
                .query(Long.class).single();
        assertThat(filas).isEqualTo(1);
        mvc.perform(con(lucia, get(fam(lucia) + "/asignaciones").param("fecha", hoy().plusDays(2).toString())))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].icono").value("caja"));
        mvc.perform(con(lucia, get(fam(lucia) + "/asignaciones").param("fecha", hoy().plusDays(4).toString())))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void unaTareaDiariaNoEntregadaCaducaAlAcabarSuDia() throws Exception {
        Sesion adulto = registrarFamilia();
        long hijo = altaHijo(adulto, "Lucía", 2016, null);
        Sesion lucia = vincularHijo(adulto, hijo);
        tareaDiaria(adulto, hijo, 10);
        long asignacion = asignacionDeHoy(lucia);
        // Simula que la asignación era de ayer: ya no se puede entregar.
        LocalDate ayer = hoy().minusDays(1);
        jdbc.sql("UPDATE asignacion_tarea SET fecha = ? WHERE id = ?").params(ayer, asignacion).update();

        mvc.perform(con(lucia, get(fam(lucia) + "/asignaciones").param("fecha", ayer.toString())))
                .andExpect(jsonPath("$[0].estado").value("CADUCADA"));
        entregar(lucia, asignacion).andExpect(status().isConflict());
        // Hoy se crea la de hoy.
        mvc.perform(con(lucia, get(fam(lucia) + "/asignaciones")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].estado").value("PENDIENTE"));
    }

    @Test
    void lasTareasMalDefinidasSeRechazan() throws Exception {
        Sesion adulto = registrarFamilia();
        long hijo = altaHijo(adulto, "Lucía", 2016, null);
        String ruta = fam(adulto) + "/tareas";
        mvc.perform(con(adulto, json(post(ruta),
                "{\"titulo\":\"X\",\"recompensa\":5,\"periodicidad\":\"SEMANAL\",\"hijos\":[%d]}".formatted(hijo))))
                .andExpect(status().isBadRequest());
        mvc.perform(con(adulto, json(post(ruta), """
                {"titulo":"X","recompensa":5,"periodicidad":"DIARIA","fechaLimite":"%s","hijos":[%d]}"""
                .formatted(hoy().plusDays(1), hijo)))).andExpect(status().isBadRequest());
        mvc.perform(con(adulto, json(post(ruta), """
                {"titulo":"X","recompensa":5,"periodicidad":"UNICA","fechaLimite":"%s","hijos":[%d]}"""
                .formatted(hoy().minusDays(1), hijo)))).andExpect(status().isBadRequest());
        mvc.perform(con(adulto, json(post(ruta),
                "{\"titulo\":\"X\",\"recompensa\":0,\"periodicidad\":\"DIARIA\",\"hijos\":[%d]}".formatted(hijo))))
                .andExpect(status().isBadRequest());
        mvc.perform(con(adulto, json(post(ruta),
                "{\"titulo\":\"X\",\"recompensa\":5,\"periodicidad\":\"DIARIA\",\"hijos\":[%d]}"
                        .formatted(adulto.miembroId()))))
                .andExpect(status().isBadRequest());
        Sesion otra = registrarFamilia();
        long ajeno = altaHijo(otra, "Ajeno", 2016, null);
        mvc.perform(con(adulto, json(post(ruta),
                "{\"titulo\":\"X\",\"recompensa\":5,\"periodicidad\":\"DIARIA\",\"hijos\":[%d]}".formatted(ajeno))))
                .andExpect(status().isBadRequest());
        mvc.perform(con(vincularHijo(adulto, hijo), json(post(ruta),
                "{\"titulo\":\"X\",\"recompensa\":5,\"periodicidad\":\"DIARIA\",\"hijos\":[%d]}".formatted(hijo))))
                .andExpect(status().isForbidden());
    }

    @Test
    void editarUnaTareaNoCambiaLoYaPactado() throws Exception {
        Sesion adulto = registrarFamilia();
        long lucia = altaHijo(adulto, "Lucía", 2016, null);
        long pablo = altaHijo(adulto, "Pablo", 2018, null);
        long tarea = crearTarea(adulto, """
                {"titulo":"Poner la mesa","recompensa":5,"periodicidad":"DIARIA","hijos":[%d,%d]}"""
                .formatted(lucia, pablo));
        mvc.perform(con(adulto, get(fam(adulto) + "/asignaciones"))).andExpect(jsonPath("$", hasSize(2)));
        String ruta = fam(adulto) + "/tareas/{id}";

        mvc.perform(con(adulto, json(patch(ruta, tarea), "{\"recompensa\":8,\"hijos\":[%d]}".formatted(lucia))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recompensa").value(8))
                .andExpect(jsonPath("$.titulo").value("Poner la mesa"))
                .andExpect(jsonPath("$.hijos", hasSize(1)));
        // La de hoy de Lucía conserva los 5 pactados y la de Pablo, pendiente, desaparece.
        mvc.perform(con(adulto, get(fam(adulto) + "/asignaciones")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].hijoId").value(lucia))
                .andExpect(jsonPath("$[0].recompensa").value(5));

        mvc.perform(con(adulto, json(patch(ruta, tarea), "{\"periodicidad\":\"UNICA\"}")))
                .andExpect(status().isBadRequest());
        mvc.perform(con(adulto, json(patch(ruta, tarea), "{}"))).andExpect(status().isBadRequest());
    }

    @Test
    void archivarUnaTareaBorraSusPendientesYLaConserva() throws Exception {
        Sesion adulto = registrarFamilia();
        long hijo = altaHijo(adulto, "Lucía", 2016, null);
        long tarea = tareaDiaria(adulto, hijo, 5);
        mvc.perform(con(adulto, get(fam(adulto) + "/asignaciones"))).andExpect(jsonPath("$", hasSize(1)));

        mvc.perform(con(adulto, delete(fam(adulto) + "/tareas/{id}", tarea))).andExpect(status().isNoContent());
        mvc.perform(con(adulto, get(fam(adulto) + "/asignaciones"))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(con(adulto, get(fam(adulto) + "/tareas"))).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(con(adulto, get(fam(adulto) + "/tareas").param("incluirArchivadas", "true")))
                .andExpect(jsonPath("$[0].archivada").value(true));
        mvc.perform(con(adulto, json(patch(fam(adulto) + "/tareas/{id}", tarea), "{\"recompensa\":9}")))
                .andExpect(status().isConflict());
    }

    @Test
    void unaFamiliaConTareasPagadasSePuedeBorrarEntera() throws Exception {
        Sesion adulto = registrarFamilia();
        long hijo = altaHijo(adulto, "Lucía", 2016, null);
        Sesion lucia = vincularHijo(adulto, hijo);
        tareaDiaria(adulto, hijo, 10);
        long asignacion = asignacionDeHoy(lucia);
        entregar(lucia, asignacion).andExpect(status().isCreated());
        revisar(adulto, asignacion, "{\"resultado\":\"APROBADA\"}", null).andExpect(status().isCreated());

        jdbc.sql("SELECT borrar_familia(?)").param(adulto.familiaId()).query().singleRow();
        Long quedan = jdbc.sql("SELECT count(*) FROM asignacion_tarea WHERE familia_id = ?")
                .param(adulto.familiaId()).query(Long.class).single();
        assertThat(quedan).isZero();
    }
}
