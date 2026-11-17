package es.upm.tfg.acceso;

import java.time.Instant;

import es.upm.tfg.familia.MiembroDto;

/** Cuerpos de las respuestas de acceso (esquemas Sesion, CodigoAcceso y Dispositivo). */
public final class Respuestas {

    private Respuestas() {
    }

    public record Sesion(String tokenAcceso, String tokenRefresco, Instant expiraEn, MiembroDto miembro) {
    }

    public record CodigoAccesoDto(String codigo, Instant expiraEn, String contenidoQr) {
    }

    public record DispositivoDto(long id, String nombre, Instant creadoEn, Instant ultimoUsoEn) {

        static DispositivoDto de(Dispositivo d) {
            return new DispositivoDto(d.getId(), d.getNombre(), d.getCreadoEn(), d.getUltimoUsoEn());
        }
    }
}
