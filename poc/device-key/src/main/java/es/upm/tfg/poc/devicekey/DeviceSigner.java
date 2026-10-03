package es.upm.tfg.poc.devicekey;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

/**
 * Lo que haría la app Android del hijo para firmar un gasto.
 *
 * En Android la clave se crearía dentro del chip seguro con
 * {@code KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")} y
 * {@code KeyGenParameterSpec} (curva secp256r1, {@code setUserAuthenticationRequired(true)} para pedir la huella).
 * La API de firma es exactamente la misma que aquí: {@code Signature.getInstance("SHA256withECDSA")}.
 * Android Keystore no soporta la curva secp256k1 de Ethereum, por eso usamos P-256 y el contrato
 * la verifica con el precompilado EIP-7951.
 */
public final class DeviceSigner {

    /** Orden de la curva P-256. */
    static final BigInteger N = new BigInteger(
            "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16);
    static final BigInteger HALF_N = N.shiftRight(1);

    private DeviceSigner() {
    }

    /** Firma (r, s) lista para el contrato, con s "baja" (s <= n/2), que es lo que exige OpenZeppelin P256. */
    public record P256Signature(byte[] r, byte[] s) {
    }

    /**
     * Reproduce {@code abi.encode(block.chainid, address(this), childId, conceptId, amount, nonce)}:
     * seis palabras de 32 bytes.
     */
    public static byte[] encodeSpend(long chainId, String contractAddress, byte[] childId, byte[] conceptId,
            BigInteger amount, BigInteger nonce) {
        byte[] out = new byte[32 * 6];
        put(out, 0, BigInteger.valueOf(chainId));
        put(out, 1, new BigInteger(contractAddress.replaceFirst("^0x", ""), 16));
        putBytes32(out, 2, childId);
        putBytes32(out, 3, conceptId);
        put(out, 4, amount);
        put(out, 5, nonce);
        return out;
    }

    /** SHA256withECDSA aplica SHA-256 al mensaje, igual que {@code sha256(...)} en el contrato. */
    public static P256Signature sign(PrivateKey key, byte[] message) throws GeneralSecurityException {
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(key);
        signer.update(message);
        return fromDer(signer.sign());
    }

    /** Coordenadas (x, y) de la clave pública: lo que el padre registra en el contrato. */
    public static byte[][] publicKeyXY(ECPublicKey publicKey) {
        return new byte[][] {
                to32(publicKey.getW().getAffineX()),
                to32(publicKey.getW().getAffineY()) };
    }

    /** Java y Android devuelven la firma en DER: SEQUENCE { INTEGER r, INTEGER s }. */
    static P256Signature fromDer(byte[] der) {
        int i = 0;
        if (der[i++] != 0x30) throw new IllegalArgumentException("No es una firma DER");
        i += (der[i] & 0x80) != 0 ? 1 + (der[i] & 0x7f) : 1; // longitud de la secuencia
        if (der[i++] != 0x02) throw new IllegalArgumentException("Falta r");
        int rLen = der[i++];
        BigInteger r = new BigInteger(1, Arrays.copyOfRange(der, i, i + rLen));
        i += rLen;
        if (der[i++] != 0x02) throw new IllegalArgumentException("Falta s");
        int sLen = der[i++];
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(der, i, i + sLen));
        // (r, s) y (r, n - s) son ambas válidas; el contrato solo acepta la "baja" para evitar duplicados.
        if (s.compareTo(HALF_N) > 0) {
            s = N.subtract(s);
        }
        return new P256Signature(to32(r), to32(s));
    }

    static byte[] to32(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, out, 32 - copy, copy);
        return out;
    }

    private static void put(byte[] out, int word, BigInteger value) {
        System.arraycopy(to32(value), 0, out, word * 32, 32);
    }

    private static void putBytes32(byte[] out, int word, byte[] value) {
        if (value.length != 32) throw new IllegalArgumentException("Se esperaban 32 bytes");
        System.arraycopy(value, 0, out, word * 32, 32);
    }
}
