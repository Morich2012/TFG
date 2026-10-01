package es.upm.tfg.poc.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SignatureVerifierTest {

    // Cuenta de prueba pública de Hardhat (#0). Nunca usar con fondos reales.
    private static final String ADDRESS = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";
    private static final String MESSAGE = "Hola desde MetaMask\nNonce: 1234";
    // Firma generada con viem (signMessage = personal_sign), la misma que produce MetaMask.
    private static final String SIGNATURE =
            "0x650628b126d61481cd3a38bee89a5f75165a23f51e63e52f125d05401ca19f43"
            + "0d3c026e826be72ca12fdba403e07ec3cdbd60cb91584d9a3730a984804d0ed51b";

    @Test
    void recuperaLaDireccionDeUnaFirmaHechaEnJavaScript() throws Exception {
        assertEquals(ADDRESS.toLowerCase(), SignatureVerifier.recoverAddress(MESSAGE, SIGNATURE));
        assertTrue(SignatureVerifier.isSignedBy(MESSAGE, SIGNATURE, ADDRESS));
    }

    @Test
    void rechazaSiElMensajeHaCambiado() {
        assertFalse(SignatureVerifier.isSignedBy(MESSAGE + " ", SIGNATURE, ADDRESS));
    }

    @Test
    void rechazaSiLaCuentaEsOtra() {
        assertFalse(SignatureVerifier.isSignedBy(MESSAGE, SIGNATURE, "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"));
    }

    @Test
    void rechazaFirmasMalFormadas() {
        assertFalse(SignatureVerifier.isSignedBy(MESSAGE, "0x1234", ADDRESS));
    }
}
