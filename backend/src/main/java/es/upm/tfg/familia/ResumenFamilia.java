package es.upm.tfg.familia;

import java.util.List;

/** Esquema {@code ResumenFamilia} (RF-27): la pantalla de inicio del adulto. */
public record ResumenFamilia(int tareasPorRevisar, int comprasPorEntregar, long totalDebidoCentimos,
        List<Hijo> hijos) {

    public record Hijo(MiembroDto miembro, long saldoMonedero, long saldoHuchas, long gastadoEstaSemana,
            long ahorradoEstaSemana) {
    }
}
