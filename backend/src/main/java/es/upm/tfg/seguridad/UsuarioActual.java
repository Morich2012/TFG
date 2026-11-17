package es.upm.tfg.seguridad;

import org.springframework.security.oauth2.jwt.Jwt;

import es.upm.tfg.familia.Rol;

/**
 * Quién hace la petición, sacado del JWT ya validado. Toda consulta se filtra por
 * {@link #familiaId()} (RNF-03).
 */
public record UsuarioActual(long miembroId, long familiaId, Rol rol, long dispositivoId) {

    static final String CLAIM_FAMILIA = "familia";
    static final String CLAIM_ROL = "rol";
    static final String CLAIM_DISPOSITIVO = "dispositivo";

    public static UsuarioActual de(Jwt jwt) {
        return new UsuarioActual(
                Long.parseLong(jwt.getSubject()),
                ((Number) jwt.getClaim(CLAIM_FAMILIA)).longValue(),
                Rol.valueOf(jwt.getClaimAsString(CLAIM_ROL)),
                ((Number) jwt.getClaim(CLAIM_DISPOSITIVO)).longValue());
    }

    public boolean esAdulto() {
        return rol.esAdulto();
    }
}
