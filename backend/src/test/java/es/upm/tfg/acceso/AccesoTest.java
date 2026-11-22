package es.upm.tfg.acceso;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.jayway.jsonpath.JsonPath;

import es.upm.tfg.PruebaIntegracion;

class AccesoTest extends PruebaIntegracion {

    @Test
    void elRegistroCreaLaFamiliaYAlAdultoComoAdministrador() throws Exception {
        String cuerpo = """
                {"nombreFamilia":"Familia Morales","nombre":"Miguel","email":"%s",
                 "contrasena":"contrasena-segura","aceptaPrivacidad":true}""".formatted(correoNuevo());
        String res = mvc.perform(json(post("/api/v1/familias"), cuerpo))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/familias/")))
                .andExpect(jsonPath("$.familiaId").isNumber())
                .andExpect(jsonPath("$.tokenAcceso").isString())
                .andExpect(jsonPath("$.tokenRefresco").isString())
                .andExpect(jsonPath("$.expiraEn").isString())
                .andExpect(jsonPath("$.miembro.rol").value("ADULTO_ADMIN"))
                .andExpect(jsonPath("$.miembro.nombre").value("Miguel"))
                .andExpect(content().string(not(containsString("\"etapa\""))))   // un adulto no tiene etapa
                .andExpect(content().string(not(containsString("email"))))
                .andReturn().getResponse().getContentAsString();
        Sesion s = Sesion.de(res);

        // La familia creada está en la ruta de la cabecera Location y la sesión actual la confirma.
        mvc.perform(con(s, get(fam(s)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Familia Morales"));
        mvc.perform(con(s, get("/api/v1/sesiones/actual"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.familiaId").value(s.familiaId()))
                .andExpect(jsonPath("$.miembro.id").value(s.miembroId()));
    }

    @Test
    void unaSesionSinTipoOConUnTipoDesconocidoSeRechaza() throws Exception {
        mvc.perform(json(post("/api/v1/sesiones"), "{\"email\":\"a@b.c\",\"contrasena\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
        mvc.perform(json(post("/api/v1/sesiones"), "{\"tipo\":\"MAGIA\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/sesiones"), "{\"tipo\":\"CONTRASENA\",\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void noSePuedeRegistrarDosVecesElMismoCorreo() throws Exception {
        String email = correoNuevo();
        registrarFamilia(email, "contrasena-segura");
        String cuerpo = """
                {"nombreFamilia":"Otra","nombre":"Otro","email":"%s",
                 "contrasena":"contrasena-segura","aceptaPrivacidad":true}""".formatted(email.toUpperCase());
        mvc.perform(json(post("/api/v1/familias"), cuerpo))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CORREO_EN_USO"));
    }

    @Test
    void unRegistroConDatosMalosDevuelveLosErroresPorCampo() throws Exception {
        String cuerpo = """
                {"nombreFamilia":"","nombre":"Mamá","email":"no-es-correo",
                 "contrasena":"corta","aceptaPrivacidad":false}""";
        mvc.perform(json(post("/api/v1/familias"), cuerpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"))
                .andExpect(jsonPath("$.errores", hasSize(4)));
    }

    @Test
    void unJsonMalFormadoTambienDevuelveDatosInvalidos() throws Exception {
        mvc.perform(json(post("/api/v1/sesiones"), "{esto no es json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("DATOS_INVALIDOS"));
    }

    @Test
    void elLoginCompruebaLaContrasena() throws Exception {
        String email = correoNuevo();
        registrarFamilia(email, "contrasena-segura");

        mvc.perform(json(post("/api/v1/sesiones"),
                "{\"tipo\":\"CONTRASENA\",\"email\":\"%s\",\"contrasena\":\"contrasena-segura\",\"nombreDispositivo\":\"Pixel\"}".formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.miembro.rol").value("ADULTO_ADMIN"));

        mvc.perform(json(post("/api/v1/sesiones"),
                "{\"tipo\":\"CONTRASENA\",\"email\":\"%s\",\"contrasena\":\"otra-cosa\"}".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        mvc.perform(json(post("/api/v1/sesiones"),
                "{\"tipo\":\"CONTRASENA\",\"email\":\"nadie@example.org\",\"contrasena\":\"contrasena-segura\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sinTokenOConUnTokenFalsoLaApiResponde401() throws Exception {
        mvc.perform(get("/api/v1/familias/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(get("/api/v1/familias/1").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void elTokenDeRefrescoSeRotaEnCadaUso() throws Exception {
        Sesion s = registrarFamilia();
        String nueva = mvc.perform(json(post("/api/v1/sesiones"),
                "{\"tipo\":\"TOKEN_REFRESCO\",\"tokenRefresco\":\"%s\"}".formatted(s.tokenRefresco())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenRefresco").value(not(s.tokenRefresco())))
                .andReturn().getResponse().getContentAsString();

        // El token viejo ya no sirve; el nuevo sí.
        mvc.perform(json(post("/api/v1/sesiones"), "{\"tipo\":\"TOKEN_REFRESCO\",\"tokenRefresco\":\"%s\"}".formatted(s.tokenRefresco())))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/v1/sesiones"),
                "{\"tipo\":\"TOKEN_REFRESCO\",\"tokenRefresco\":\"%s\"}".formatted(JsonPath.<String>read(nueva, "$.tokenRefresco"))))
                .andExpect(status().isCreated());
    }

    @Test
    void cerrarSesionRevocaElTokenDeRefresco() throws Exception {
        Sesion s = registrarFamilia();
        mvc.perform(con(s, delete("/api/v1/sesiones/actual"))).andExpect(status().isNoContent());
        mvc.perform(json(post("/api/v1/sesiones"), "{\"tipo\":\"TOKEN_REFRESCO\",\"tokenRefresco\":\"%s\"}".formatted(s.tokenRefresco())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void elMovilDelHijoSeVinculaConUnCodigoDeUnSoloUso() throws Exception {
        Sesion adulto = registrarFamilia();
        long hijo = altaHijo(adulto, "Lucía", 2017, null);

        String codigo = JsonPath.read(mvc.perform(con(adulto,
                post(fam(adulto) + "/miembros/{id}/codigos-vinculacion", hijo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contenidoQr").isString())
                .andReturn().getResponse().getContentAsString(), "$.codigo");

        String peticion = "{\"tipo\":\"CODIGO_VINCULACION\",\"codigo\":\"%s\",\"nombreDispositivo\":\"Tablet de Lucía\"}";
        mvc.perform(json(post("/api/v1/sesiones"), peticion.formatted(codigo.toLowerCase())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.miembro.id").value(hijo))
                .andExpect(jsonPath("$.miembro.rol").value("HIJO"))
                .andExpect(jsonPath("$.miembro.etapa").value(1));

        mvc.perform(json(post("/api/v1/sesiones"), peticion.formatted(codigo)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.codigo").value("CODIGO_CADUCADO"));

        mvc.perform(con(adulto, get(fam(adulto) + "/miembros/{id}/dispositivos", hijo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].nombre").value("Tablet de Lucía"));
    }

    @Test
    void unHijoNoPuedeHacerOperacionesDeAdulto() throws Exception {
        Sesion adulto = registrarFamilia();
        Sesion hijo = vincularHijo(adulto, altaHijo(adulto, "Pablo", 2015, null));

        mvc.perform(con(hijo, json(post(fam(hijo) + "/miembros"),
                "{\"nombre\":\"Intruso\",\"avatar\":\"a\",\"consentimiento\":true}")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
        mvc.perform(con(hijo, post(fam(hijo) + "/invitaciones"))).andExpect(status().isForbidden());

        // Pero sí puede ver su familia.
        mvc.perform(con(hijo, get(fam(hijo) + "/miembros")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void elPinSeCompruebaConLimiteDeIntentos() throws Exception {
        Sesion adulto = registrarFamilia();
        Sesion hijo = vincularHijo(adulto, altaHijo(adulto, "Marta", 2016, "1234"));

        String token = mvc.perform(json(post("/api/v1/sesiones"),
                "{\"tipo\":\"TOKEN_REFRESCO\",\"tokenRefresco\":\"%s\",\"pin\":\"1234\"}".formatted(hijo.tokenRefresco())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String refresco = JsonPath.read(token, "$.tokenRefresco");

        String malo = "{\"tipo\":\"TOKEN_REFRESCO\",\"tokenRefresco\":\"%s\",\"pin\":\"0000\"}".formatted(refresco);
        for (int i = 0; i < 5; i++) {
            mvc.perform(json(post("/api/v1/sesiones"), malo)).andExpect(status().isUnauthorized());
        }
        // Al sexto intento está bloqueado, aunque el PIN sea el bueno.
        mvc.perform(json(post("/api/v1/sesiones"),
                "{\"tipo\":\"TOKEN_REFRESCO\",\"tokenRefresco\":\"%s\",\"pin\":\"1234\"}".formatted(refresco)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("DEMASIADOS_INTENTOS"));
    }

    @Test
    void unSegundoAdultoSeUneConUnaInvitacion() throws Exception {
        Sesion admin = registrarFamilia();
        String codigo = JsonPath.read(mvc.perform(con(admin, post(fam(admin) + "/invitaciones")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.codigo");

        String res = mvc.perform(json(post("/api/v1/sesiones"), """
                {"tipo":"CODIGO_INVITACION","codigo":"%s","nombre":"Papá","email":"%s","contrasena":"contrasena-segura",
                 "aceptaPrivacidad":true}""".formatted(codigo, correoNuevo())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.miembro.rol").value("ADULTO"))
                .andReturn().getResponse().getContentAsString();
        Sesion papa = Sesion.de(res);

        // Están en la misma familia, pero solo el administrador invita.
        org.assertj.core.api.Assertions.assertThat(familiaJson(papa)).isEqualTo(familiaJson(admin));
        mvc.perform(con(papa, post(fam(papa) + "/invitaciones"))).andExpect(status().isForbidden());
    }

    @Test
    void unCodigoDeVinculacionNoSirveParaUnirseComoAdulto() throws Exception {
        Sesion adulto = registrarFamilia();
        long hijo = altaHijo(adulto, "Lucía", 2017, null);
        String codigo = JsonPath.read(mvc.perform(con(adulto,
                post(fam(adulto) + "/miembros/{id}/codigos-vinculacion", hijo)))
                .andReturn().getResponse().getContentAsString(), "$.codigo");
        mvc.perform(json(post("/api/v1/sesiones"), """
                {"tipo":"CODIGO_INVITACION","codigo":"%s","nombre":"Intruso","email":"%s","contrasena":"contrasena-segura",
                 "aceptaPrivacidad":true}""".formatted(codigo, correoNuevo())))
                .andExpect(status().isGone());
    }
}
