package es.upm.tfg.poc.child;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Lo que el móvil firma y cómo se entrega la firma al contrato. Java puro (sin Android) para
 * poder probarlo con JUnit en el PC; es la misma lógica que poc/device-key/DeviceSigner.
 */
public final class SpendMessage {

    /** Orden de la curva P-256. */
    static final BigInteger N = new BigInteger(
            "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16);
    static final BigInteger HALF_N = N.shiftRight(1);

    private SpendMessage() {
    }

    /**
     * Reproduce {@code abi.encode(block.chainid, address(this), childId, conceptId, amount, nonce)} de
     * DeviceKeyLedger.spendDigest. El contrato le aplica sha256, igual que SHA256withECDSA en Android.
     */
    public static byte[] encode(long chainId, String contract, byte[] childId, byte[] conceptId,
            BigInteger amount, BigInteger nonce) {
        byte[] out = new byte[32 * 6];
        System.arraycopy(to32(BigInteger.valueOf(chainId)), 0, out, 0, 32);
        System.arraycopy(to32(new BigInteger(contract.replaceFirst("^0x", ""), 16)), 0, out, 32, 32);
        System.arraycopy(check32(childId), 0, out, 64, 32);
        System.arraycopy(check32(conceptId), 0, out, 96, 32);
        System.arraycopy(to32(amount), 0, out, 128, 32);
        System.arraycopy(to32(nonce), 0, out, 160, 32);
        return out;
    }

    /** Android devuelve la firma en DER: SEQUENCE { INTEGER r, INTEGER s }. El contrato quiere (r, s) con s baja. */
    public static byte[][] rsFromDer(byte[] der) {
        int i = 0;
        if (der[i++] != 0x30) throw new IllegalArgumentException("No es una firma DER");
        i += (der[i] & 0x80) != 0 ? 1 + (der[i] & 0x7f) : 1;
        if (der[i++] != 0x02) throw new IllegalArgumentException("Falta r");
        int rLen = der[i++];
        BigInteger r = new BigInteger(1, Arrays.copyOfRange(der, i, i + rLen));
        i += rLen;
        if (der[i++] != 0x02) throw new IllegalArgumentException("Falta s");
        int sLen = der[i++];
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(der, i, i + sLen));
        if (s.compareTo(HALF_N) > 0) {
            s = N.subtract(s);
        }
        return new byte[][] { to32(r), to32(s) };
    }

    /** Identificadores del contrato (hijo, concepto, tarea): sha256 del texto. El backend hace lo mismo. */
    public static byte[] id(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] to32(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] out = new byte[32];
        int n = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - n, out, 32 - n, n);
        return out;
    }

    public static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder("0x");
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        }
        return sb.toString();
    }

    private static byte[] check32(byte[] value) {
        if (value.length != 32) throw new IllegalArgumentException("Se esperaban 32 bytes");
        return value;
    }
}
