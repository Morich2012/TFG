package es.upm.tfg.seguridad;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Valores de {@code app.seguridad.*} en application.yaml. */
@ConfigurationProperties("app.seguridad")
public record PropiedadesSeguridad(
        String jwtSecreto,
        Duration duracionTokenAcceso,
        Duration duracionCodigoVinculacion,
        Duration duracionCodigoInvitacion,
        int intentosPin,
        Duration bloqueoPin) {
}
