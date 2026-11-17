package es.upm.tfg.familia;

/**
 * Evento que publica {@link ServicioFamilia} al dar de baja a un miembro. El módulo de acceso lo
 * escucha para revocar sus dispositivos y códigos, sin que familia dependa de acceso.
 */
public record MiembroDadoDeBaja(long miembroId, long familiaId) {
}
