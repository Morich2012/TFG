package es.upm.tfg.poc.relayer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.Transfer;
import org.web3j.utils.Convert;

/**
 * Flujo completo del relayer contra una red local de Hardhat (con el precompilado P-256 de Osaka):
 * desplegar, registrar el "móvil", recompensar y gastar con una firma P-256 hecha como en Android.
 *
 * Se ejecuta solo si hay un nodo: en poc/contracts, {@code npx hardhat node}, y después
 * {@code LOCAL_RPC_URL=http://127.0.0.1:8545 mvn test}.
 */
@EnabledIfEnvironmentVariable(named = "LOCAL_RPC_URL", matches = ".+")
class DeviceKeyLedgerClientTest {

    /** Cuenta #0 de Hardhat: pública y solo para la red local, sirve para dar gas al relayer. */
    private static final String HARDHAT_FUNDER =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final BigInteger N = new BigInteger(
            "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16);

    private static DeviceKeyLedgerClient ledger;
    private static KeyPair phone;
    private static byte[] childId;

    @BeforeAll
    static void deployAndRegister() throws Exception {
        Web3j web3j = Web3j.build(new HttpService(System.getenv("LOCAL_RPC_URL")));
        Path dataDir = Files.createTempDirectory("relayer");
        ledger = new DeviceKeyLedgerClient(web3j, dataDir, null);
        Transfer.sendFunds(web3j, Credentials.create(HARDHAT_FUNDER), ledger.relayerAddress(),
                java.math.BigDecimal.ONE, Convert.Unit.ETHER).send();

        ledger.deploy();
        assertEquals(ledger.relayerAddress().toLowerCase(), ledger.parent().toLowerCase());

        KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
        gen.initialize(new ECGenParameterSpec("secp256r1"));
        phone = gen.generateKeyPair();
        ECPublicKey pub = (ECPublicKey) phone.getPublic();
        childId = DeviceKeyLedgerClient.toBytes32("child:test");
        assertTrue(ledger.registerDevice(childId, to32(pub.getW().getAffineX()), to32(pub.getW().getAffineY()))
                .isStatusOK());
        assertTrue(ledger.reward(childId, DeviceKeyLedgerClient.toBytes32("tarea:cama"), BigInteger.TEN).isStatusOK());
    }

    @Test
    void gastoFirmadoPorElMovilYRechazos() throws Exception {
        DeviceKeyLedgerClient.ChildState before = ledger.child(childId);
        assertTrue(before.registered());
        assertEquals(BigInteger.TEN, before.balance());

        byte[] concept = DeviceKeyLedgerClient.toBytes32("concepto:cromos");
        byte[][] sig = sign(concept, BigInteger.valueOf(4), before.nonce());

        // El relayer no puede cambiar la cantidad: la simulación lo detecta y no se envía nada.
        LedgerException tampered = assertThrows(LedgerException.class,
                () -> ledger.spendSigned(childId, concept, BigInteger.valueOf(9), sig[0], sig[1]));
        assertEquals("InvalidSignature", tampered.code());

        String hash = ledger.spendSigned(childId, concept, BigInteger.valueOf(4), sig[0], sig[1]);
        waitMined(hash);
        assertEquals(BigInteger.valueOf(6), ledger.child(childId).balance());

        // La misma firma no vale dos veces (el nonce ya ha cambiado).
        LedgerException replay = assertThrows(LedgerException.class,
                () -> ledger.spendSigned(childId, concept, BigInteger.valueOf(4), sig[0], sig[1]));
        assertEquals("InvalidSignature", replay.code());

        byte[][] tooMuch = sign(concept, BigInteger.valueOf(50), ledger.child(childId).nonce());
        LedgerException broke = assertThrows(LedgerException.class,
                () -> ledger.spendSigned(childId, concept, BigInteger.valueOf(50), tooMuch[0], tooMuch[1]));
        assertEquals("InsufficientBalance", broke.code());
    }

    @Test
    void tareaRepetidaYMovilDesconocido() {
        LedgerException dup = assertThrows(LedgerException.class,
                () -> ledger.reward(childId, DeviceKeyLedgerClient.toBytes32("tarea:cama"), BigInteger.ONE));
        assertEquals("TaskAlreadyRewarded", dup.code());
        assertFalse(ledger.child(DeviceKeyLedgerClient.toBytes32("child:nadie")).registered());
    }

    /** Lo mismo que hace la app: abi.encode(...) firmado con SHA256withECDSA, DER → (r, s) con s baja. */
    private static byte[][] sign(byte[] concept, BigInteger amount, BigInteger nonce) throws Exception {
        byte[] msg = new byte[32 * 6];
        System.arraycopy(to32(BigInteger.valueOf(ledger.chainId())), 0, msg, 0, 32);
        System.arraycopy(to32(new BigInteger(ledger.contractAddressOrNull().substring(2), 16)), 0, msg, 32, 32);
        System.arraycopy(childId, 0, msg, 64, 32);
        System.arraycopy(concept, 0, msg, 96, 32);
        System.arraycopy(to32(amount), 0, msg, 128, 32);
        System.arraycopy(to32(nonce), 0, msg, 160, 32);
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(phone.getPrivate());
        s.update(msg);
        byte[] der = s.sign();
        int i = 3;
        int rLen = der[i++];
        BigInteger r = new BigInteger(1, Arrays.copyOfRange(der, i, i + rLen));
        i += rLen + 1;
        int sLen = der[i++];
        BigInteger sv = new BigInteger(1, Arrays.copyOfRange(der, i, i + sLen));
        if (sv.compareTo(N.shiftRight(1)) > 0) {
            sv = N.subtract(sv);
        }
        return new byte[][] { to32(r), to32(sv) };
    }

    private static void waitMined(String hash) throws InterruptedException {
        for (int i = 0; i < 30 && ledger.receipt(hash) == null; i++) {
            Thread.sleep(200);
        }
        assertTrue(ledger.receipt(hash).isStatusOK());
    }

    private static byte[] to32(BigInteger v) {
        byte[] raw = v.toByteArray();
        byte[] out = new byte[32];
        int n = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - n, out, 32 - n, n);
        return out;
    }
}
