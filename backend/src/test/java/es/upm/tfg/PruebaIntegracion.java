package es.upm.tfg;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/** Base de las pruebas de integración: aplicación completa + PostgreSQL en Testcontainers. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ContenedoresPrueba.class)
public abstract class PruebaIntegracion {

    @Autowired
    protected MockMvc mvc;

    /** Datos de una sesión devuelta por la API. */
    protected record Sesion(String tokenAcceso, String tokenRefresco, long miembroId) {

        public static Sesion de(String json) {
            return new Sesion(JsonPath.read(json, "$.tokenAcceso"), JsonPath.read(json, "$.tokenRefresco"),
                    ((Number) JsonPath.read(json, "$.miembro.id")).longValue());
        }
    }

    protected static String correoNuevo() {
        return "adulto-" + UUID.randomUUID() + "@example.org";
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(cuerpo);
    }

    protected static MockHttpServletRequestBuilder con(Sesion s, MockHttpServletRequestBuilder peticion) {
        return peticion.header("Authorization", "Bearer " + s.tokenAcceso());
    }

    /** Registra una familia nueva y devuelve la sesión de su administrador. */
    protected Sesion registrarFamilia(String email, String contrasena) throws Exception {
        String cuerpo = """
                {"nombreFamilia":"Familia de prueba","nombre":"Mamá","email":"%s",
                 "contrasena":"%s","aceptaPrivacidad":true}""".formatted(email, contrasena);
        String res = mvc.perform(json(post("/api/v1/auth/registro"), cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Sesion.de(res);
    }

    protected Sesion registrarFamilia() throws Exception {
        return registrarFamilia(correoNuevo(), "contrasena-segura");
    }

    /** Da de alta un hijo y devuelve su id. */
    protected long altaHijo(Sesion adulto, String nombre, Integer anio, String pin) throws Exception {
        String cuerpo = """
                {"nombre":"%s","avatar":"avatar-3",%s%s"consentimiento":true}""".formatted(nombre,
                anio == null ? "" : "\"anioNacimiento\":" + anio + ",",
                pin == null ? "" : "\"pin\":\"" + pin + "\",");
        String res = mvc.perform(con(adulto, json(post("/api/v1/familia/miembros"), cuerpo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    /** Genera el código QR de un hijo y lo canjea como haría su móvil. */
    protected Sesion vincularHijo(Sesion adulto, long hijoId) throws Exception {
        String codigo = JsonPath.read(mvc.perform(con(adulto,
                post("/api/v1/familia/miembros/{id}/codigo-vinculacion", hijoId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.codigo");
        String res = mvc.perform(json(post("/api/v1/auth/vincular"),
                "{\"codigo\":\"%s\",\"nombreDispositivo\":\"Tablet\"}".formatted(codigo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Sesion.de(res);
    }

    protected String familiaJson(Sesion s) throws Exception {
        return mvc.perform(con(s, get("/api/v1/familia"))).andReturn().getResponse().getContentAsString();
    }
}
