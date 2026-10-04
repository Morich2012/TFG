package es.upm.tfg.poc.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

class AuthControllerTest {

    private final Credentials user = Credentials.create(
            "0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d");

    private String sign(String message) {
        Sign.SignatureData s = Sign.signPrefixedMessage(message.getBytes(StandardCharsets.UTF_8), user.getEcKeyPair());
        byte[] out = new byte[65];
        System.arraycopy(s.getR(), 0, out, 0, 32);
        System.arraycopy(s.getS(), 0, out, 32, 32);
        out[64] = s.getV()[0];
        return Numeric.toHexString(out);
    }

    @Test
    void flujoCompletoYNonceDeUnSoloUso() {
        AuthController controller = new AuthController();
        var challenge = controller.challenge(new AuthController.ChallengeRequest(user.getAddress()));
        String signature = sign(challenge.message());

        var ok = controller.verify(new AuthController.VerifyRequest(challenge.nonce(), signature));
        assertEquals(200, ok.getStatusCode().value());
        assertTrue(ok.getBody().valid());

        // Reutilizar la misma firma (replay) debe fallar
        var replay = controller.verify(new AuthController.VerifyRequest(challenge.nonce(), signature));
        assertEquals(400, replay.getStatusCode().value());
    }

    @Test
    void rechazaFirmaDeOtraCuenta() {
        AuthController controller = new AuthController();
        var challenge = controller.challenge(new AuthController.ChallengeRequest(
                "0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC"));
        var res = controller.verify(new AuthController.VerifyRequest(challenge.nonce(), sign(challenge.message())));
        assertEquals(401, res.getStatusCode().value());
    }
}
