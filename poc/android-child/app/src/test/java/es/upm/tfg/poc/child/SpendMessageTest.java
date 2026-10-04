package es.upm.tfg.poc.child;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;

import org.junit.Test;

public class SpendMessageTest {

    /**
     * Firma de poc/contracts/test/fixtures/java-device-signature.json, que el test del contrato
     * acepta en la red simulada. Si la app codifica igual, esa firma verifica sobre su mensaje.
     */
    @Test
    public void codificaIgualQueElContrato() throws Exception {
        byte[] childId = SpendMessage.id("child:1");
        byte[] conceptId = SpendMessage.id("concepto:cromos");
        assertEquals("0x835ad22437ee946e4610695f67fa2f0bc2b53990c257d263c5b71f5e2bd0dd71", SpendMessage.hex(childId));
        assertEquals("0xa977e82eb0eaf82278c069b0237689d4e2eccfc1b39e26151f430bef71c6dde6", SpendMessage.hex(conceptId));

        byte[] message = SpendMessage.encode(31337, "0x5FbDB2315678afecb367f032d93F642f64180aa3",
                childId, conceptId, BigInteger.valueOf(4), BigInteger.ZERO);
        PublicKey key = publicKey(
                "6555592fec658f905af03c6ebd2bd9071f108338906b3d5ff48c7488e21bb683",
                "4466a85324ccdf51178a613eb2a0d8520a3d5abef106ced458c295c172648273");
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(key);
        verifier.update(message);
        assertTrue(verifier.verify(toDer(
                new BigInteger("83a269867f621bc2292b115a99a110d1203fedf4dc92c651d0f43e7e4019f486", 16),
                new BigInteger("2093980a777d0d2134594a87d40ec289d20a4129429cd27960cedb1d26904daa", 16))));
    }

    @Test
    public void normalizaASBajaSinRomperLaFirma() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = gen.generateKeyPair();
        byte[] message = SpendMessage.encode(11155111, "0x00000000000000000000000000000000000000ff",
                new byte[32], new byte[32], BigInteger.TEN, BigInteger.ONE);
        for (int i = 0; i < 50; i++) {
            Signature signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign(pair.getPrivate());
            signer.update(message);
            byte[][] rs = SpendMessage.rsFromDer(signer.sign());
            BigInteger s = new BigInteger(1, rs[1]);
            assertTrue(s.compareTo(SpendMessage.HALF_N) <= 0);

            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(pair.getPublic());
            verifier.update(message);
            assertTrue(verifier.verify(toDer(new BigInteger(1, rs[0]), s)));
        }
    }

    private static PublicKey publicKey(String x, String y) throws Exception {
        AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec("secp256r1"));
        ECParameterSpec spec = params.getParameterSpec(ECParameterSpec.class);
        ECPoint point = new ECPoint(new BigInteger(x, 16), new BigInteger(y, 16));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, spec));
    }

    private static byte[] toDer(BigInteger r, BigInteger s) {
        byte[] rr = r.toByteArray();
        byte[] ss = s.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(0x30);
        out.write(4 + rr.length + ss.length);
        out.write(0x02);
        out.write(rr.length);
        out.write(rr, 0, rr.length);
        out.write(0x02);
        out.write(ss.length);
        out.write(ss, 0, ss.length);
        return out.toByteArray();
    }
}
