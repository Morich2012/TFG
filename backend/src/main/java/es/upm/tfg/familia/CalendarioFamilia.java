package es.upm.tfg.familia;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Fechas vistas desde la familia: "hoy" para las tareas y "esta semana" para el resumen se
 * calculan con su zona horaria ({@code familia.zona_horaria}), no con la del servidor.
 */
@Component
public class CalendarioFamilia {

    private final JdbcClient jdbc;
    private final Clock reloj;

    public CalendarioFamilia(JdbcClient jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    public ZoneId zona(long familiaId) {
        return ZoneId.of(jdbc.sql("SELECT zona_horaria FROM familia WHERE id = ?")
                .param(familiaId).query(String.class).single());
    }

    public LocalDate hoy(long familiaId) {
        return LocalDate.now(reloj.withZone(zona(familiaId)));
    }

    /** Lunes de la semana en curso. */
    public LocalDate inicioSemana(long familiaId) {
        return hoy(familiaId).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
