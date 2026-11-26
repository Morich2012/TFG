package es.upm.tfg.cuentas;

import java.util.List;

/** Esquema {@code PaginaMovimientos}. */
public record PaginaMovimientos(List<MovimientoDto> contenido, int pagina, int tamano, long totalElementos) {
}
