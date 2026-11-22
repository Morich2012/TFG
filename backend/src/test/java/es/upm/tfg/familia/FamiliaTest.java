package es.upm.tfg.familia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

import com.jayway.jsonpath.JsonPath;

import es.upm.tfg.PruebaIntegracion;

class FamiliaTest extends PruebaIntegracion {

    @Autowired
    JdbcClient jdbc;

    @Test
    void unAdultoCambiaElNombreDeLaMonedaYElTipoDeCambio() throws Exception {
        Sesion s = registrarFamilia();
        mvc.perform(con(s, json(patch(fam(s)), "{\"nombreMoneda\":\"bellotas\",\"monedasPorEuro\":20}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Familia de prueba"))
                .andExpect(jsonPath("$.nombreMoneda").value("bellotas"))
                .andExpect(jsonPath("$.monedasPorEuro").value(20));

        mvc.perform(con(s, json(patch(fam(s)), "{}")))
                .andExpect(status().isBadRequest());
        mvc.perform(con(s, json(patch(fam(s)), "{\"monedasPorEuro\":0}")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elAltaDeUnHijoCreaSuMonederoYGuardaSoloElAnioDeNacimiento() throws Exception {
        Sesion s = registrarFamilia();
        long hijo = altaHijo(s, "Lucía", 2018, "4321");

        mvc.perform(con(s, get(fam(s) + "/miembros")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[1].nombre").value("Lucía"))
                .andExpect(jsonPath("$[1].rol").value("HIJO"))
                .andExpect(jsonPath("$[1].anioNacimiento").value(2018))
                .andExpect(jsonPath("$[1].nivel").value(1))
                .andExpect(jsonPath("$[1].etapa").value(1))
                .andExpect(jsonPath("$[1].pin").doesNotExist());

        Long monederos = jdbc.sql("SELECT count(*) FROM cuenta WHERE titular_id = ? AND tipo = 'MONEDERO'")
                .param(hijo).query(Long.class).single();
        assertThat(monederos).isEqualTo(1);
        Boolean fila = jdbc.sql("SELECT email IS NULL AND hash_contrasena IS NULL AND consentimiento_en IS NOT NULL "
                + "AND hash_pin LIKE '{bcrypt}%' FROM miembro WHERE id = ?").param(hijo).query(Boolean.class).single();
        assertThat(fila).isTrue();
    }

    @Test
    void elAltaSinConsentimientoSeRechaza() throws Exception {
        Sesion s = registrarFamilia();
        mvc.perform(con(s, json(post(fam(s) + "/miembros"),
                "{\"nombre\":\"Lucía\",\"avatar\":\"avatar-2\",\"consentimiento\":false}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("consentimiento"));
    }

    @Test
    void laRutaDeOtraFamiliaRespondeNoEncontrado() throws Exception {
        Sesion familiaA = registrarFamilia();
        Sesion familiaB = registrarFamilia();
        mvc.perform(con(familiaA, get(fam(familiaB))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mvc.perform(con(familiaA, get(fam(familiaB) + "/miembros"))).andExpect(status().isNotFound());
        mvc.perform(con(familiaA, json(post(fam(familiaB) + "/miembros"),
                "{\"nombre\":\"Intruso\",\"avatar\":\"a\",\"consentimiento\":true}")))
                .andExpect(status().isNotFound());
    }

    @Test
    void elAltaDevuelveLaUbicacionDelNuevoMiembro() throws Exception {
        Sesion s = registrarFamilia();
        String ubicacion = mvc.perform(con(s, json(post(fam(s) + "/miembros"),
                "{\"nombre\":\"Lucía\",\"avatar\":\"avatar-2\",\"consentimiento\":true}")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        assertThat(ubicacion).startsWith(fam(s) + "/miembros/");
        mvc.perform(con(s, get(ubicacion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Lucía"));
    }

    @Test
    void unMiembroDeOtraFamiliaNoExiste() throws Exception {
        Sesion familiaA = registrarFamilia();
        Sesion familiaB = registrarFamilia();
        long hijoDeB = altaHijo(familiaB, "Hugo", 2014, null);

        mvc.perform(con(familiaA, json(patch(fam(familiaA) + "/miembros/{id}", hijoDeB), "{\"nombre\":\"X\"}")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        mvc.perform(con(familiaA, post(fam(familiaA) + "/miembros/{id}/codigos-vinculacion", hijoDeB)))
                .andExpect(status().isNotFound());
        mvc.perform(con(familiaA, get(fam(familiaA) + "/miembros/{id}/dispositivos", hijoDeB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void reglasAlEditarMiembros() throws Exception {
        Sesion admin = registrarFamilia();
        long hijo = altaHijo(admin, "Lucía", 2017, null);

        mvc.perform(con(admin, json(patch(fam(admin) + "/miembros/{id}", hijo),
                "{\"nombre\":\"Lu\",\"anioNacimiento\":2016,\"pin\":\"9876\"}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Lu"))
                .andExpect(jsonPath("$.anioNacimiento").value(2016));

        // El PIN y el año son solo para hijos.
        mvc.perform(con(admin, json(patch(fam(admin) + "/miembros/{id}", admin.miembroId()), "{\"pin\":\"1234\"}")))
                .andExpect(status().isBadRequest());

        // Un adulto que no es administrador no puede editar al administrador.
        Sesion papa = unirseComoAdulto(admin);
        mvc.perform(con(papa, json(patch(fam(papa) + "/miembros/{id}", admin.miembroId()), "{\"nombre\":\"X\"}")))
                .andExpect(status().isForbidden());
        mvc.perform(con(papa, json(patch(fam(papa) + "/miembros/{id}", papa.miembroId()), "{\"nombre\":\"Papá\"}")))
                .andExpect(status().isOk());
    }

    @Test
    void laBajaAnonimizaAlMiembroYCierraSusSesiones() throws Exception {
        Sesion admin = registrarFamilia();
        long hijo = altaHijo(admin, "Lucía", 2017, "1234");
        Sesion movilHijo = vincularHijo(admin, hijo);

        mvc.perform(con(admin, delete(fam(admin) + "/miembros/{id}", hijo))).andExpect(status().isNoContent());

        mvc.perform(con(admin, get(fam(admin) + "/miembros"))).andExpect(jsonPath("$", hasSize(1)));
        mvc.perform(json(post("/api/v1/sesiones"), "{\"tipo\":\"TOKEN_REFRESCO\",\"tokenRefresco\":\"%s\"}".formatted(movilHijo.tokenRefresco())))
                .andExpect(status().isUnauthorized());
        String datos = jdbc.sql("SELECT nombre || '|' || coalesce(anio_nacimiento::text, '-') || '|' "
                + "|| coalesce(hash_pin, '-') FROM miembro WHERE id = ?").param(hijo).query(String.class).single();
        assertThat(datos).isEqualTo("Miembro dado de baja|-|-");

        mvc.perform(con(admin, delete(fam(admin) + "/miembros/{id}", hijo))).andExpect(status().isNotFound());
    }

    @Test
    void soloElAdministradorDaDeBajaYNadieLoDaDeBajaAEl() throws Exception {
        Sesion admin = registrarFamilia();
        Sesion papa = unirseComoAdulto(admin);
        long hijo = altaHijo(admin, "Lucía", 2017, null);

        mvc.perform(con(papa, delete(fam(papa) + "/miembros/{id}", hijo))).andExpect(status().isForbidden());
        mvc.perform(con(admin, delete(fam(admin) + "/miembros/{id}", admin.miembroId())))
                .andExpect(status().isForbidden());

        // Un adulto dado de baja ya no puede entrar.
        mvc.perform(con(admin, delete(fam(admin) + "/miembros/{id}", papa.miembroId())))
                .andExpect(status().isNoContent());
        mvc.perform(json(post("/api/v1/sesiones"), "{\"tipo\":\"TOKEN_REFRESCO\",\"tokenRefresco\":\"%s\"}".formatted(papa.tokenRefresco())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unAdultoRevocaElMovilPerdidoDeUnHijo() throws Exception {
        Sesion admin = registrarFamilia();
        long hijo = altaHijo(admin, "Lucía", 2017, null);
        Sesion movil = vincularHijo(admin, hijo);
        long dispositivo = ((Number) JsonPath.read(mvc.perform(con(admin,
                get(fam(admin) + "/miembros/{id}/dispositivos", hijo)))
                .andReturn().getResponse().getContentAsString(), "$[0].id")).longValue();

        // Otra familia no puede tocarlo.
        Sesion otra = registrarFamilia();
        mvc.perform(con(otra, delete(fam(otra) + "/miembros/{m}/dispositivos/{id}", hijo, dispositivo))).andExpect(status().isNotFound());

        mvc.perform(con(admin, delete(fam(admin) + "/miembros/{m}/dispositivos/{id}", hijo, dispositivo))).andExpect(status().isNoContent());
        mvc.perform(json(post("/api/v1/sesiones"), "{\"tipo\":\"TOKEN_REFRESCO\",\"tokenRefresco\":\"%s\"}".formatted(movil.tokenRefresco())))
                .andExpect(status().isUnauthorized());
    }

    private Sesion unirseComoAdulto(Sesion admin) throws Exception {
        String codigo = JsonPath.read(mvc.perform(con(admin, post(fam(admin) + "/invitaciones")))
                .andReturn().getResponse().getContentAsString(), "$.codigo");
        return Sesion.de(mvc.perform(json(post("/api/v1/sesiones"), """
                {"tipo":"CODIGO_INVITACION","codigo":"%s","nombre":"Papá","email":"%s","contrasena":"contrasena-segura",
                 "aceptaPrivacidad":true}""".formatted(codigo, correoNuevo())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }
}
