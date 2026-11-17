package es.upm.tfg.seguridad;

import java.time.Instant;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import es.upm.tfg.familia.Miembro;

/** Emite el token de acceso: un JWT firmado con HMAC-SHA256 que dura 15 minutos. */
@Service
public class ServicioTokens {

    private final JwtEncoder codificador;
    private final PropiedadesSeguridad propiedades;

    public ServicioTokens(JwtEncoder codificador, PropiedadesSeguridad propiedades) {
        this.codificador = codificador;
        this.propiedades = propiedades;
    }

    public TokenAcceso emitir(Miembro miembro, long dispositivoId) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(propiedades.duracionTokenAcceso());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("tfg-backend")
                .issuedAt(ahora)
                .expiresAt(expira)
                .subject(String.valueOf(miembro.getId()))
                .claim(UsuarioActual.CLAIM_FAMILIA, miembro.getFamiliaId())
                .claim(UsuarioActual.CLAIM_ROL, miembro.getRol().name())
                .claim(UsuarioActual.CLAIM_DISPOSITIVO, dispositivoId)
                .build();
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = codificador.encode(JwtEncoderParameters.from(cabecera, claims)).getTokenValue();
        return new TokenAcceso(token, expira);
    }

    public record TokenAcceso(String valor, Instant expiraEn) {
    }
}
