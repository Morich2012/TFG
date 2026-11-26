package es.upm.tfg.tareas;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Set;

/** Días de una tarea SEMANAL. En la base de datos se guardan como máscara de bits (lunes = 1 ... domingo = 64). */
public enum DiaSemana {
    LUNES,
    MARTES,
    MIERCOLES,
    JUEVES,
    VIERNES,
    SABADO,
    DOMINGO;

    public int bit() {
        return 1 << ordinal();
    }

    public static DiaSemana de(DayOfWeek dia) {
        return values()[dia.getValue() - 1];
    }

    public static Short mascara(Set<DiaSemana> dias) {
        if (dias == null || dias.isEmpty()) {
            return null;
        }
        return (short) dias.stream().mapToInt(DiaSemana::bit).sum();
    }

    public static Set<DiaSemana> deMascara(Short mascara) {
        Set<DiaSemana> dias = EnumSet.noneOf(DiaSemana.class);
        if (mascara != null) {
            for (DiaSemana d : values()) {
                if ((mascara & d.bit()) != 0) {
                    dias.add(d);
                }
            }
        }
        return dias;
    }
}
