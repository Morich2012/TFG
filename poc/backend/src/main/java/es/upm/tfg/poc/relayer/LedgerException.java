package es.upm.tfg.poc.relayer;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.web3j.crypto.Hash;

/** Error que se devuelve a la app con un código estable y un mensaje que se puede enseñar al niño. */
public class LedgerException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public LedgerException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    /** Errores personalizados de DeviceKeyLedger (y de la librería P256), por su selector de 4 bytes. */
    private static final Map<String, String[]> CONTRACT_ERRORS = Map.of(
            selector("NotParent()"), new String[] { "NotParent", "Solo el padre puede hacer esto" },
            selector("InvalidPublicKey()"), new String[] { "InvalidPublicKey", "La clave del móvil no es válida" },
            selector("UnknownChild(bytes32)"), new String[] { "UnknownChild", "Este móvil aún no está registrado" },
            selector("ZeroAmount()"), new String[] { "ZeroAmount", "La cantidad debe ser mayor que cero" },
            selector("TaskAlreadyRewarded(bytes32)"),
            new String[] { "TaskAlreadyRewarded", "Esta tarea ya se había recompensado" },
            selector("InvalidSignature()"), new String[] { "InvalidSignature", "La firma no es de este móvil" },
            selector("InsufficientBalance(uint256,uint256)"),
            new String[] { "InsufficientBalance", "No tienes saldo suficiente" },
            selector("MissingPrecompile(address)"),
            new String[] { "MissingPrecompile", "Esta red no verifica firmas P-256 (falta EIP-7951)" });

    private static final Pattern HEX = Pattern.compile("0x[0-9a-fA-F]{8,}");

    static String selector(String signature) {
        return Hash.sha3String(signature).substring(0, 10);
    }

    /** Traduce el "revert" de una simulación (eth_call / eth_estimateGas) a un error legible. */
    static LedgerException fromRevert(String errorData, String nodeMessage) {
        if (errorData != null) {
            Matcher m = HEX.matcher(errorData);
            if (m.find()) {
                String[] known = CONTRACT_ERRORS.get(m.group().substring(0, 10).toLowerCase());
                if (known != null) {
                    return new LedgerException(HttpStatus.UNPROCESSABLE_CONTENT, known[0], known[1]);
                }
            }
        }
        return new LedgerException(HttpStatus.UNPROCESSABLE_CONTENT, "Reverted",
                "El contrato rechazó la operación: " + nodeMessage);
    }
}
