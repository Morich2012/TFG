package es.upm.tfg.poc.auth;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SignatureException;
import java.util.Arrays;

import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

/**
 * Verifica firmas personal_sign (EIP-191) hechas por una cuenta EOA de MetaMask.
 * El backend no tiene ninguna clave privada: solo recupera la dirección que firmó.
 */
public final class SignatureVerifier {

    private SignatureVerifier() {
    }

    /** Devuelve la dirección (en minúsculas, con 0x) que firmó {@code message}. */
    public static String recoverAddress(String message, String signatureHex) throws SignatureException {
        byte[] sig = Numeric.hexStringToByteArray(signatureHex);
        if (sig.length != 65) {
            throw new SignatureException("La firma debe tener 65 bytes y tiene " + sig.length);
        }
        byte v = sig[64];
        if (v < 27) {
            v += 27;
        }
        Sign.SignatureData data = new Sign.SignatureData(
                v, Arrays.copyOfRange(sig, 0, 32), Arrays.copyOfRange(sig, 32, 64));
        BigInteger publicKey = Sign.signedPrefixedMessageToKey(
                message.getBytes(StandardCharsets.UTF_8), data);
        return "0x" + Keys.getAddress(publicKey);
    }

    public static boolean isSignedBy(String message, String signatureHex, String expectedAddress) {
        try {
            return recoverAddress(message, signatureHex).equalsIgnoreCase(expectedAddress);
        } catch (SignatureException | IllegalArgumentException e) {
            return false;
        }
    }
}
