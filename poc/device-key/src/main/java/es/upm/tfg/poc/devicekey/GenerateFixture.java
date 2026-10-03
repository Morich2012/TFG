package es.upm.tfg.poc.devicekey;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.HexFormat;

/**
 * Genera una clave P-256 y firma un gasto, igual que haría el móvil del hijo, y lo guarda como
 * fixture para el test de interoperabilidad Java → Solidity de {@code poc/contracts}.
 *
 * Uso: mvn -q compile exec:java -Dexec.args="../contracts/test/fixtures/java-device-signature.json"
 */
public final class GenerateFixture {

    // Valores deterministas de la red simulada de Hardhat: chainId 31337 y el primer contrato
    // desplegado por la cuenta #0 en una red recién creada.
    static final long CHAIN_ID = 31337;
    static final String CONTRACT = "0x5FbDB2315678afecb367f032d93F642f64180aa3";

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "java-device-signature.json");

        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();

        byte[] childId = sha256("child:1");
        byte[] conceptId = sha256("concepto:cromos");
        BigInteger amount = BigInteger.valueOf(4);
        BigInteger nonce = BigInteger.ZERO;

        byte[] message = DeviceSigner.encodeSpend(CHAIN_ID, CONTRACT, childId, conceptId, amount, nonce);
        DeviceSigner.P256Signature sig = DeviceSigner.sign(keyPair.getPrivate(), message);
        byte[][] xy = DeviceSigner.publicKeyXY((ECPublicKey) keyPair.getPublic());

        HexFormat hex = HexFormat.of();
        String json = """
                {
                  "generatedBy": "poc/device-key (Java %s, SHA256withECDSA, secp256r1)",
                  "chainId": %d,
                  "contract": "%s",
                  "childId": "0x%s",
                  "conceptId": "0x%s",
                  "amount": "%s",
                  "nonce": "%s",
                  "x": "0x%s",
                  "y": "0x%s",
                  "r": "0x%s",
                  "s": "0x%s"
                }
                """.formatted(System.getProperty("java.version"), CHAIN_ID, CONTRACT,
                hex.formatHex(childId), hex.formatHex(conceptId), amount, nonce,
                hex.formatHex(xy[0]), hex.formatHex(xy[1]), hex.formatHex(sig.r()), hex.formatHex(sig.s()));
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out, json);
        System.out.println("Firma del dispositivo guardada en " + out);
    }

    static byte[] sha256(String text) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
    }
}
