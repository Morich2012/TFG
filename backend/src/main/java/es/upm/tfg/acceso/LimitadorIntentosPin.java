package es.upm.tfg.acceso;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import es.upm.tfg.seguridad.PropiedadesSeguridad;

/**
 * Límite de intentos de PIN por dispositivo: tras {@code intentos-pin} fallos seguidos se bloquea
 * durante {@code bloqueo-pin}. Un PIN de 4 cifras solo tiene 10.000 combinaciones, así que sin este
 * límite se podría adivinar.
 *
 * <p>Se guarda en memoria: basta para un único servidor. Con varios habría que llevarlo a la base
 * de datos o a Redis.
 */
@Component
public class LimitadorIntentosPin {

    private final Map<Long, Fallos> fallos = new ConcurrentHashMap<>();
    private final PropiedadesSeguridad propiedades;
    private final Clock reloj;

    public LimitadorIntentosPin(PropiedadesSeguridad propiedades, Clock reloj) {
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    public boolean estaBloqueado(long dispositivoId) {
        Fallos f = fallos.get(dispositivoId);
        return f != null && f.bloqueadoHasta() != null && reloj.instant().isBefore(f.bloqueadoHasta());
    }

    public void registrarFallo(long dispositivoId) {
        fallos.compute(dispositivoId, (id, f) -> {
            int n = (f == null || f.bloqueadoHasta() != null) ? 1 : f.numero() + 1;
            Instant bloqueo = n >= propiedades.intentosPin() ? reloj.instant().plus(propiedades.bloqueoPin()) : null;
            return new Fallos(n, bloqueo);
        });
    }

    public void registrarAcierto(long dispositivoId) {
        fallos.remove(dispositivoId);
    }

    private record Fallos(int numero, Instant bloqueadoHasta) {
    }
}
