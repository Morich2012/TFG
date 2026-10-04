package es.upm.tfg.poc.devicekey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;

import org.junit.jupiter.api.Test;

class DeviceSignerTest {

    @Test
    void laFirmaNormalizadaSigueSiendoValidaYTieneSBaja() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        byte[] message = DeviceSigner.encodeSpend(31337, "0x5FbDB2315678afecb367f032d93F642f64180aa3",
                new byte[32], new byte[32], BigInteger.TEN, BigInteger.ZERO);

        for (int i = 0; i < 50; i++) { // ECDSA es aleatorio: probamos muchas firmas
            DeviceSigner.P256Signature sig = DeviceSigner.sign(keyPair.getPrivate(), message);
            BigInteger s = new BigInteger(1, sig.s());
            assertTrue(s.compareTo(DeviceSigner.HALF_N) <= 0, "s debe ser baja");

            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(keyPair.getPublic());
            verifier.update(message);
            assertTrue(verifier.verify(toDer(sig.r(), sig.s())), "la firma normalizada debe verificar");
        }
    }

    @Test
    void codificaComoAbiEncode() {
        byte[] encoded = DeviceSigner.encodeSpend(1, "0x00000000000000000000000000000000000000ff",
                new byte[32], new byte[32], BigInteger.TWO, BigInteger.ONE);
        assertEquals(192, encoded.length);
        assertEquals(1, encoded[31]);           // chainId
        assertEquals((byte) 0xff, encoded[63]); // dirección del contrato
        assertEquals(2, encoded[159]);          // amount
        assertEquals(1, encoded[191]);          // nonce
    }

    private static byte[] toDer(byte[] r, byte[] s) {
        byte[] rr = new BigInteger(1, r).toByteArray();
        byte[] ss = new BigInteger(1, s).toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x30);
        out.write(4 + rr.length + ss.length);
        out.write(0x02);
        out.write(rr.length);
        out.writeBytes(rr);
        out.write(0x02);
        out.write(ss.length);
        out.writeBytes(ss);
        return out.toByteArray();
    }
}
