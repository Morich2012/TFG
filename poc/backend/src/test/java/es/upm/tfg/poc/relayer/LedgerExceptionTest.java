package es.upm.tfg.poc.relayer;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.junit.jupiter.api.Test;
import org.web3j.utils.Numeric;

class LedgerExceptionTest {

    @Test
    void traduceLosErroresDelContrato() {
        // InsufficientBalance(10, 50) tal como lo devuelve el nodo en error.data
        String data = LedgerException.selector("InsufficientBalance(uint256,uint256)")
                + "000000000000000000000000000000000000000000000000000000000000000a"
                + "0000000000000000000000000000000000000000000000000000000000000032";
        LedgerException e = LedgerException.fromRevert(data, "execution reverted");
        assertEquals("InsufficientBalance", e.code());
        assertEquals("No tienes saldo suficiente", e.getMessage());

        assertEquals("InvalidSignature",
                LedgerException.fromRevert("\"" + LedgerException.selector("InvalidSignature()") + "\"", "x").code());
        assertEquals("Reverted", LedgerException.fromRevert(null, "out of gas").code());
    }

    @Test
    void identificadoresComoHexOComoTexto() throws Exception {
        String hex = "0x" + "ab".repeat(32);
        assertArrayEquals(Numeric.hexStringToByteArray(hex), DeviceKeyLedgerClient.toBytes32(hex));
        byte[] expected = MessageDigest.getInstance("SHA-256").digest("child:hijo".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(expected, DeviceKeyLedgerClient.toBytes32("child:hijo"));
    }
}
