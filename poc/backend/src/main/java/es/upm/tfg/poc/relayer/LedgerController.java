package es.upm.tfg.poc.relayer;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

/**
 * API que usa la app Android del hijo (PoC 2).
 *
 * Los identificadores (childId, conceptId, task) pueden ir como "0x" + 64 hex o como texto,
 * en cuyo caso se usa sha256(texto). La app siempre manda el hex que ella misma calcula y firma.
 */
@RestController
@RequestMapping("/ledger")
public class LedgerController {

    private final DeviceKeyLedgerClient ledger;

    public LedgerController(DeviceKeyLedgerClient ledger) {
        this.ledger = ledger;
    }

    public record Status(long chainId, String contract, String parent, String relayer, BigDecimal relayerBalanceEth) {
    }

    public record TxResponse(String txHash, String status, BigInteger gasUsed, String explorer) {
    }

    public record DeployResponse(String contract, String txHash, BigInteger gasUsed, String explorer) {
    }

    public record ChildResponse(String childId, BigInteger balance, BigInteger nonce, boolean registered,
            long chainId, String contract) {
    }

    public record DeviceRequest(String x, String y) {
    }

    public record RewardRequest(String task, BigInteger amount) {
    }

    public record SpendRequest(String conceptId, BigInteger amount, String r, String s) {
    }

    public record ErrorResponse(String error, String message) {
    }

    @GetMapping
    public Status status() {
        String contract = ledger.contractAddressOrNull();
        return new Status(ledger.chainId(), contract, contract == null ? null : ledger.parent(),
                ledger.relayerAddress(), ledger.relayerBalanceEth());
    }

    @PostMapping("/deploy")
    public DeployResponse deploy() {
        TransactionReceipt r = ledger.deploy();
        return new DeployResponse(r.getContractAddress(), r.getTransactionHash(), r.getGasUsed(),
                explorer(r.getTransactionHash()));
    }

    @GetMapping("/children/{childId}")
    public ChildResponse child(@PathVariable String childId) {
        byte[] id = DeviceKeyLedgerClient.toBytes32(childId);
        DeviceKeyLedgerClient.ChildState c = ledger.child(id);
        return new ChildResponse(Numeric.toHexString(id), c.balance(), c.nonce(), c.registered(),
                ledger.chainId(), ledger.contractAddressOrNull());
    }

    @PostMapping("/children/{childId}/device")
    public TxResponse registerDevice(@PathVariable String childId, @RequestBody DeviceRequest req) {
        return mined(ledger.registerDevice(DeviceKeyLedgerClient.toBytes32(childId),
                DeviceKeyLedgerClient.hex32(req.x(), "x"), DeviceKeyLedgerClient.hex32(req.y(), "y")));
    }

    @PostMapping("/children/{childId}/rewards")
    public TxResponse reward(@PathVariable String childId, @RequestBody RewardRequest req) {
        return mined(ledger.reward(DeviceKeyLedgerClient.toBytes32(childId),
                DeviceKeyLedgerClient.toBytes32(req.task()), positive(req.amount())));
    }

    /** Responde en cuanto la transacción está enviada: la app enseña "¡Hecho!" y consulta /tx después. */
    @PostMapping("/children/{childId}/spends")
    public ResponseEntity<TxResponse> spend(@PathVariable String childId, @RequestBody SpendRequest req) {
        String hash = ledger.spendSigned(DeviceKeyLedgerClient.toBytes32(childId),
                DeviceKeyLedgerClient.toBytes32(req.conceptId()), positive(req.amount()),
                DeviceKeyLedgerClient.hex32(req.r(), "r"), DeviceKeyLedgerClient.hex32(req.s(), "s"));
        return ResponseEntity.accepted().body(new TxResponse(hash, "pending", null, explorer(hash)));
    }

    @GetMapping("/tx/{hash}")
    public TxResponse tx(@PathVariable String hash) {
        TransactionReceipt r = ledger.receipt(hash);
        return r == null ? new TxResponse(hash, "pending", null, explorer(hash)) : mined(r);
    }

    @ExceptionHandler(LedgerException.class)
    public ResponseEntity<ErrorResponse> onLedgerError(LedgerException e) {
        return ResponseEntity.status(e.status()).body(new ErrorResponse(e.code(), e.getMessage()));
    }

    private TxResponse mined(TransactionReceipt r) {
        return new TxResponse(r.getTransactionHash(), r.isStatusOK() ? "success" : "failed", r.getGasUsed(),
                explorer(r.getTransactionHash()));
    }

    private String explorer(String txHash) {
        return ledger.chainId() == 11155111L ? "https://sepolia.etherscan.io/tx/" + txHash : null;
    }

    private static BigInteger positive(BigInteger amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new LedgerException(HttpStatus.BAD_REQUEST, "ZeroAmount",
                    "La cantidad debe ser mayor que cero");
        }
        return amount;
    }
}
