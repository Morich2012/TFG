package es.upm.tfg.cuentas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import es.upm.tfg.PruebaIntegracion;

/** Cuentas, historial, ajustes manuales y correcciones por la API (RF-09, RF-10, RF-12, RF-16). */
class CuentasTest extends PruebaIntegracion {

    private ResultActions ajuste(Sesion adulto, long hijo, long cantidad, String clave) throws Exception {
        var peticion = json(post(fam(adulto) + "/ajustes"),
                "{\"hijoId\":%d,\"cantidad\":%d,\"concepto\":\"Regalo de la abuela\"}".formatted(hijo, cantidad));
        if (clave != null) {
            peticion = peticion.header("Idempotency-Key", clave);
        }
        return mvc.perform(con(adulto, peticion));
    }

    private long monedero(Sesion s, long hijo) throws Exception {
        String res = mvc.perform(con(s, get(fam(s) + "/cuentas").param("titularId", Long.toString(hijo))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$[0].id")).longValue();
    }

    private long saldo(Sesion s, long cuenta) throws Exception {
        String res = mvc.perform(con(s, get(fam(s) + "/cuentas/{id}", cuenta)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.saldo")).longValue();
    }

    @Test
    void unHijoVeSoloSusCuentasYUnAdultoLasDeTodosSinElBanco() throws Exception {
        Sesion adulto = registrarFamilia();
        long lucia = altaHijo(adulto, "Lucía", 2016, null);
        long pablo = altaHijo(adulto, "Pablo", 2018, null);
        Sesion sesionLucia = vincularHijo(adulto, lucia);

        mvc.perform(con(adulto, get(fam(adulto) + "/cuentas")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].tipo").value("MONEDERO"))
                .andExpect(jsonPath("$[0].saldo").value(0))
                .andExpect(jsonPath("$[0].saldoEuros").value(0.0));
        mvc.perform(con(adulto, get(fam(adulto) + "/cuentas").param("tipo", "BANCO")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].titularId").doesNotExist());

        mvc.perform(con(sesionLucia, get(fam(adulto) + "/cuentas")))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].titularId").value(lucia));
        mvc.perform(con(sesionLucia, get(fam(adulto) + "/cuentas/{id}", monedero(adulto, pablo))))
                .andExpect(status().isNotFound());
    }

    @Test
    void unAjusteIngresaORetiraYNuncaDejaElMonederoEnNegativo() throws Exception {
        Sesion adulto = registrarFamilia();
        long hijo = altaHijo(adulto, "Lucía", 2016, null);
        long monedero = monedero(adulto, hijo);

        ajuste(adulto, hijo, 50, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("AJUSTE_MANUAL"))
                .andExpect(jsonPath("$.cantidad").value(50))
                .andExpect(jsonPath("$.cuentaDestinoId").value(monedero))
                .andExpect(jsonPath("$.autorId").value(adulto.miembroId()));
        ajuste(adulto, hijo, -20, null).andExpect(status().isCreated())
                .andExpect(jsonPath("$.cuentaOrigenId").value(monedero));
        ajuste(adulto, hijo, -31, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"))
                .andExpect(jsonPath("$.detail").value("Faltan 1 moneda"));
        assertThat(saldo(adulto, monedero)).isEqualTo(30);

        ajuste(adulto, hijo, 0, null).andExpect(status().isBadRequest());
        Sesion otra = registrarFamilia();
        long hijoAjeno = altaHijo(otra, "Ajeno", 2016, null);
        ajuste(adulto, hijoAjeno, 5, null).andExpect(status().isBadRequest());
        ajuste(adulto, adulto.miembroId(), 5, null).andExpect(status().isBadRequest());
    }

    @Test
    void unHijoNoPuedeHacerAjustes() throws Exception {
        Sesion adulto = registrarFamilia();
        long hijo = altaHijo(adulto, "Lucía", 2016, null);
        ajuste(vincularHijo(adulto, hijo), hijo, 50, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("PROHIBIDO"));
    }

    @Test
    void laMismaIdempotencyKeyNoCobraDosVeces() throws Exception {
        Sesion adulto = registrarFamilia();
        long hijo = altaHijo(adulto, "Lucía", 2016, null);
        String primero = ajuste(adulto, hijo, 25, "clave-123").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String segundo = ajuste(adulto, hijo, 25, "clave-123").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat((Object) JsonPath.read(segundo, "$.id"))
                .isEqualTo(JsonPath.read(primero, "$.id"));
        assertThat(saldo(adulto, monedero(adulto, hijo))).isEqualTo(25);
    }

    @Test
    void elHistorialVaDelMasRecienteAlMasAntiguoConElSaldoTrasCadaMovimiento() throws Exception {
        Sesion adulto = registrarFamilia();
        long hijo = altaHijo(adulto, "Lucía", 2016, null);
        long monedero = monedero(adulto, hijo);
        ajuste(adulto, hijo, 50, null).andExpect(status().isCreated());
        ajuste(adulto, hijo, -20, null).andExpect(status().isCreated());
        String ruta = fam(adulto) + "/cuentas/" + monedero + "/movimientos";

        mvc.perform(con(adulto, get(ruta).param("tamano", "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElementos").value(2))
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].cantidad").value(20))
                .andExpect(jsonPath("$.contenido[0].sentido").value("SALIDA"))
                .andExpect(jsonPath("$.contenido[0].saldoTras").value(30));
        mvc.perform(con(adulto, get(ruta).param("tamano", "1").param("pagina", "1")))
                .andExpect(jsonPath("$.contenido[0].sentido").value("ENTRADA"))
                .andExpect(jsonPath("$.contenido[0].saldoTras").value(50));
        mvc.perform(con(adulto, get(ruta).param("tipo", "PAGA")))
                .andExpect(jsonPath("$.totalElementos").value(0));
        mvc.perform(con(adulto, get(ruta).param("tamano", "101"))).andExpect(status().isBadRequest());
        mvc.perform(con(vincularHijo(adulto, hijo), get(ruta))).andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(2)));
    }

    @Test
    void unaCorreccionDevuelveElDineroUnaSolaVez() throws Exception {
        Sesion adulto = registrarFamilia();
        long hijo = altaHijo(adulto, "Lucía", 2016, null);
        long monedero = monedero(adulto, hijo);
        long original = ((Number) JsonPath.read(ajuste(adulto, hijo, 40, null)
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        String cuerpo = "{\"concepto\":\"Me equivoqué de hijo\"}";
        String ruta = fam(adulto) + "/movimientos/{id}/correccion";

        String correccion = mvc.perform(con(adulto, json(put(ruta, original), cuerpo)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("CORRECCION"))
                .andExpect(jsonPath("$.corrigeAId").value(original))
                .andExpect(jsonPath("$.cuentaOrigenId").value(monedero))
                .andReturn().getResponse().getContentAsString();
        assertThat(saldo(adulto, monedero)).isZero();

        mvc.perform(con(adulto, json(put(ruta, original), cuerpo)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("YA_CORREGIDO"));
        mvc.perform(con(adulto, json(put(ruta, (Object) JsonPath.read(correccion, "$.id")), cuerpo)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
        mvc.perform(con(adulto, json(put(ruta, 999999), cuerpo))).andExpect(status().isNotFound());
    }

    @Test
    void unaCorreccionNoPuedeDejarAlHijoEnNegativo() throws Exception {
        Sesion adulto = registrarFamilia();
        long hijo = altaHijo(adulto, "Lucía", 2016, null);
        long original = ((Number) JsonPath.read(ajuste(adulto, hijo, 40, null)
                .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
        ajuste(adulto, hijo, -30, null).andExpect(status().isCreated());
        mvc.perform(con(adulto, json(put(fam(adulto) + "/movimientos/{id}/correccion", original),
                "{\"concepto\":\"Anular\"}")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("SALDO_INSUFICIENTE"));
    }

    @Test
    void lasCategoriasComunesLasVeTodaLaFamilia() throws Exception {
        Sesion adulto = registrarFamilia();
        long hijo = altaHijo(adulto, "Lucía", 2016, null);
        mvc.perform(con(vincularHijo(adulto, hijo), get(fam(adulto) + "/categorias")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nombre", hasItem("Otros")));
    }
}
