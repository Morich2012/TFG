package es.upm.tfg.familia;

import java.time.Clock;
import java.time.Year;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * Calcula la etapa de un hijo (RF-34). Se abre una etapa cuando el hijo tiene la edad y el
 * nivel mínimos de la tabla {@code etapa}; los padres pueden fijarla a mano.
 *
 * <p>Como solo se guarda el año de nacimiento (RNF-05), la edad es la que cumple este año.
 */
@Service
public class ServicioEtapas {

    private final JdbcClient jdbc;
    private final Clock reloj;
    private volatile List<Etapa> etapas;

    public ServicioEtapas(JdbcClient jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    /** Etapa del miembro, o {@code null} si es un adulto. */
    public Integer etapaDe(Miembro m) {
        if (m.getRol().esAdulto()) {
            return null;
        }
        if (m.getEtapaFijada() != null) {
            return m.getEtapaFijada().intValue();
        }
        if (m.getAnioNacimiento() == null) {
            return 1;
        }
        int edad = Year.now(reloj).getValue() - m.getAnioNacimiento();
        int nivel = m.getNivel();
        return etapas().stream()
                .filter(e -> edad >= e.edadMinima() && nivel >= e.nivelMinimo())
                .mapToInt(Etapa::numero)
                .max()
                .orElse(1);
    }

    /** El catálogo lo carga la migración V1 y no cambia en ejecución, así que se lee una vez. */
    private List<Etapa> etapas() {
        List<Etapa> actual = etapas;
        if (actual == null) {
            actual = jdbc.sql("SELECT numero, edad_minima, nivel_minimo FROM etapa ORDER BY numero")
                    .query((rs, i) -> new Etapa(rs.getInt(1), rs.getInt(2), rs.getInt(3)))
                    .list();
            etapas = actual;
        }
        return actual;
    }

    private record Etapa(int numero, int edadMinima, int nivelMinimo) {
    }
}
