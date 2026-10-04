package es.upm.tfg.poc.auth;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nivel 2 de la PoC: "login con MetaMask".
 * 1) El cliente pide un reto (mensaje con nonce de un solo uso).
 * 2) MetaMask lo firma con personal_sign.
 * 3) El backend recupera la dirección y comprueba que coincide.
 * En memoria para la PoC; en el TFG irían a PostgreSQL/Redis y se emitiría un JWT.
 */
@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "http://localhost:5173")
public class AuthController {

    private static final long NONCE_TTL_SECONDS = 300;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();

    record Challenge(String address, String message, Instant expiresAt) {
    }

    record ChallengeRequest(String address) {
    }

    record ChallengeResponse(String nonce, String message) {
    }

    record VerifyRequest(String nonce, String signature) {
    }

    record VerifyResponse(boolean valid, String address, String reason) {
    }

    @PostMapping("/challenge")
    public ChallengeResponse challenge(@RequestBody ChallengeRequest req) {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String nonce = HexFormat.of().formatHex(bytes);
        Instant now = Instant.now();
        String message = """
                TFG PoC quiere que inicies sesión con tu cuenta:
                %s

                Nonce: %s
                Emitido: %s""".formatted(req.address(), nonce, now);
        challenges.put(nonce, new Challenge(req.address(), message, now.plusSeconds(NONCE_TTL_SECONDS)));
        return new ChallengeResponse(nonce, message);
    }

    @PostMapping("/verify")
    public ResponseEntity<VerifyResponse> verify(@RequestBody VerifyRequest req) {
        // remove(): el nonce solo sirve una vez (evita reutilizar una firma capturada)
        Challenge c = challenges.remove(req.nonce());
        if (c == null) {
            return ResponseEntity.badRequest().body(new VerifyResponse(false, null, "nonce desconocido o ya usado"));
        }
        if (Instant.now().isAfter(c.expiresAt())) {
            return ResponseEntity.badRequest().body(new VerifyResponse(false, c.address(), "nonce caducado"));
        }
        boolean ok = SignatureVerifier.isSignedBy(c.message(), req.signature(), c.address());
        return ok
                ? ResponseEntity.ok(new VerifyResponse(true, c.address(), null))
                : ResponseEntity.status(401).body(new VerifyResponse(false, c.address(), "la firma no corresponde a la cuenta"));
    }
}
