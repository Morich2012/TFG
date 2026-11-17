package es.upm.tfg.familia;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.util.ReflectionTestUtils;

import es.upm.tfg.PruebaIntegracion;

/** Etapas según la tabla etapa de V1: edad 8/9/11/13/16 y nivel 1/5/10/16/22. */
class ServicioEtapasTest extends PruebaIntegracion {

    @Autowired
    JdbcClient jdbc;

    private final ServicioEtapas etapas2026() {
        Clock reloj = Clock.fixed(Instant.parse("2026-11-17T10:00:00Z"), ZoneId.of("Europe/Madrid"));
        return new ServicioEtapas(jdbc, reloj);
    }

    private static Miembro hijo(int anio, int nivel) {
        Miembro m = Miembro.hijo(1, "Prueba", "avatar-1", (short) anio, null);
        ReflectionTestUtils.setField(m, "nivel", (short) nivel);
        return m;
    }

    @Test
    void laEtapaDependeDeLaEdadYDelNivel() {
        ServicioEtapas etapas = etapas2026();
        assertThat(etapas.etapaDe(hijo(2018, 1))).isEqualTo(1);   // 8 años
        assertThat(etapas.etapaDe(hijo(2016, 1))).isEqualTo(1);   // 10 años, pero nivel 1
        assertThat(etapas.etapaDe(hijo(2016, 5))).isEqualTo(2);   // 10 años y nivel 5
        assertThat(etapas.etapaDe(hijo(2017, 30))).isEqualTo(2);  // nivel alto, pero 9 años
        assertThat(etapas.etapaDe(hijo(2009, 22))).isEqualTo(5);  // 17 años y nivel 22
        assertThat(etapas.etapaDe(hijo(2020, 1))).isEqualTo(1);   // menor de 8: etapa 1
    }

    @Test
    void losPadresPuedenFijarLaEtapaYLosAdultosNoTienen() {
        ServicioEtapas etapas = etapas2026();
        Miembro m = hijo(2018, 1);
        ReflectionTestUtils.setField(m, "etapaFijada", (short) 3);
        assertThat(etapas.etapaDe(m)).isEqualTo(3);
        assertThat(etapas.etapaDe(Miembro.adulto(1, Rol.ADULTO, "Mamá", "a@b.c", "x"))).isNull();
    }
}
