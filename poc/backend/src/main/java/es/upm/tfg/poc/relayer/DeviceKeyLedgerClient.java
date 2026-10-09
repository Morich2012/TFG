package es.upm.tfg.poc.relayer;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.utils.Convert;
import org.web3j.utils.Numeric;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Relayer de la PoC 2: habla con DeviceKeyLedger en nombre de la app del hijo.
 *
 * El backend firma las transacciones con su propia clave (paga el gas), pero el contrato solo
 * ejecuta un gasto si (r, s) es una firma P-256 válida del móvil registrado del hijo.
 * Antes de enviar nada se simula con eth_call: si el contrato lo va a rechazar, no se gasta gas.
 */
@Service
public class DeviceKeyLedgerClient {

    private static final Pattern BYTES32 = Pattern.compile("0x[0-9a-fA-F]{64}");

    private static final Logger log = LoggerFactory.getLogger(DeviceKeyLedgerClient.class);

    private final Web3j web3j;
    private final Credentials relayer;
    private final Path dataDir;
    private final String bytecode;
    private volatile Long chainId;
    private volatile String contractAddress;

    @Autowired
    public DeviceKeyLedgerClient(
            @Value("${relayer.rpc-url}") String rpcUrl,
            @Value("${relayer.data-dir}") String dataDir,
            @Value("${relayer.contract-address:}") String contractAddress) {
        this(Web3j.build(new HttpService(rpcUrl)), Path.of(dataDir), contractAddress);
    }

    DeviceKeyLedgerClient(Web3j web3j, Path dataDir, String contractAddress) {
        this.web3j = web3j;
        this.dataDir = dataDir;
        this.relayer = RelayerKey.loadOrCreate(dataDir);
        this.bytecode = loadBytecode();
        this.contractAddress = contractAddress == null || contractAddress.isBlank() ? null : contractAddress;
        log.info("Relayer {} (envíale SepoliaETH para el gas). Estado: GET /ledger", relayer.getAddress());
    }

    // ---------- estado ----------

    public String relayerAddress() {
        return relayer.getAddress();
    }

    public long chainId() {
        if (chainId == null) {
            chainId = rpc(() -> web3j.ethChainId().send()).getChainId().longValueExact();
        }
        return chainId;
    }

    public BigDecimal relayerBalanceEth() {
        BigInteger wei = rpc(() -> web3j.ethGetBalance(relayer.getAddress(), DefaultBlockParameterName.LATEST).send())
                .getBalance();
        return Convert.fromWei(new BigDecimal(wei), Convert.Unit.ETHER);
    }

    /** Dirección del contrato: la configurada, o la que guardó el último despliegue en esta red. */
    public String contractAddressOrNull() {
        if (contractAddress == null) {
            Path saved = savedAddressFile();
            if (Files.exists(saved)) {
                try {
                    contractAddress = Files.readString(saved, StandardCharsets.UTF_8).trim();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        return contractAddress;
    }

    private String contract() {
        String address = contractAddressOrNull();
        if (address == null) {
            throw new LedgerException(HttpStatus.CONFLICT, "NoContract",
                    "Aún no hay contrato: despliégalo con POST /ledger/deploy");
        }
        return address;
    }

    // ---------- lecturas (gratis, sin transacción) ----------

    public String parent() {
        return ((Address) call("parent", List.of(), List.of(new TypeReference<Address>() { })).get(0)).getValue();
    }

    public record ChildState(BigInteger balance, BigInteger nonce, boolean registered, String x, String y) {
    }

    public ChildState child(byte[] childId) {
        BigInteger balance = uint("balanceOf", childId);
        BigInteger nonce = uint("nonceOf", childId);
        List<Type> key = call("deviceKeyOf", List.of(new Bytes32(childId)),
                List.of(new TypeReference<Bytes32>() { }, new TypeReference<Bytes32>() { }));
        byte[] x = ((Bytes32) key.get(0)).getValue();
        byte[] y = ((Bytes32) key.get(1)).getValue();
        boolean registered = !BigInteger.ZERO.equals(new BigInteger(1, x)) || !BigInteger.ZERO.equals(new BigInteger(1, y));
        return new ChildState(balance, nonce, registered, Numeric.toHexString(x), Numeric.toHexString(y));
    }

    private BigInteger uint(String name, byte[] childId) {
        return ((Uint256) call(name, List.of(new Bytes32(childId)), List.of(new TypeReference<Uint256>() { })).get(0))
                .getValue();
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private List<Type> call(String name, List<Type> inputs, List<TypeReference<?>> outputs) {
        Function f = new Function(name, inputs, (List) outputs);
        EthCall result = rpc(() -> web3j.ethCall(
                Transaction.createEthCallTransaction(relayer.getAddress(), contract(), FunctionEncoder.encode(f)),
                DefaultBlockParameterName.LATEST).send());
        return FunctionReturnDecoder.decode(result.getValue(), f.getOutputParameters());
    }

    // ---------- escrituras (las paga el relayer) ----------

    /** Despliega DeviceKeyLedger. Quien despliega es el "padre": aquí, el relayer. Espera a que se mine. */
    public synchronized TransactionReceipt deploy() {
        TransactionReceipt receipt = waitForReceipt(sendRaw("", bytecode));
        if (!receipt.isStatusOK()) {
            throw new LedgerException(HttpStatus.BAD_GATEWAY, "DeployFailed", "El despliegue falló en la red");
        }
        contractAddress = receipt.getContractAddress();
        try {
            Files.createDirectories(dataDir);
            Files.writeString(savedAddressFile(), contractAddress + "\n", StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return receipt;
    }

    /** PoC: el backend actúa de padre y registra la clave pública del móvil. Espera a que se mine. */
    public TransactionReceipt registerDevice(byte[] childId, byte[] x, byte[] y) {
        return waitForReceipt(transact(new Function("registerDevice",
                List.of(new Bytes32(childId), new Bytes32(x), new Bytes32(y)), List.of())));
    }

    /** PoC: el backend actúa de padre y da una recompensa. Espera a que se mine. */
    public TransactionReceipt reward(byte[] childId, byte[] taskId, BigInteger amount) {
        return waitForReceipt(transact(new Function("reward",
                List.of(new Bytes32(childId), new Bytes32(taskId), new Uint256(amount)), List.of())));
    }

    /** El gasto firmado por el móvil. No espera: devuelve el hash y la app consulta el estado después. */
    public String spendSigned(byte[] childId, byte[] conceptId, BigInteger amount, byte[] r, byte[] s) {
        return transact(new Function("spendSigned",
                List.of(new Bytes32(childId), new Bytes32(conceptId), new Uint256(amount), new Bytes32(r), new Bytes32(s)),
                List.of()));
    }

    /** null mientras está pendiente. */
    public TransactionReceipt receipt(String txHash) {
        return rpc(() -> web3j.ethGetTransactionReceipt(txHash).send()).getTransactionReceipt().orElse(null);
    }

    private String transact(Function f) {
        String to = contract();
        String data = FunctionEncoder.encode(f);
        // 1) Simulación: si el contrato va a revertir (firma falsa, sin saldo...) no se envía ni se paga gas.
        EthCall simulation = rpc(() -> web3j.ethCall(
                Transaction.createEthCallTransaction(relayer.getAddress(), to, data),
                DefaultBlockParameterName.LATEST).send(), false);
        if (simulation.hasError()) {
            throw LedgerException.fromRevert(simulation.getError().getData(), simulation.getError().getMessage());
        }
        if (simulation.isReverted()) {
            throw LedgerException.fromRevert(simulation.getRevertReasonEncodedData(), simulation.getRevertReason());
        }
        // 2) Envío real
        return sendRaw(to, data);
    }

    /** Construye, firma y envía una transacción EIP-1559. synchronized: el nonce del relayer es secuencial. */
    private synchronized String sendRaw(String to, String data) {
        String from = relayer.getAddress();
        Transaction estimateTx = to.isEmpty()
                ? Transaction.createContractTransaction(from, null, null, data)
                : Transaction.createFunctionCallTransaction(from, null, null, null, to, data);
        EthEstimateGas estimate = rpc(() -> web3j.ethEstimateGas(estimateTx).send(), false);
        if (estimate.hasError()) {
            throw LedgerException.fromRevert(estimate.getError().getData(), estimate.getError().getMessage());
        }
        BigInteger gasLimit = estimate.getAmountUsed().multiply(BigInteger.valueOf(12)).divide(BigInteger.TEN);
        BigInteger nonce = rpc(() -> web3j.ethGetTransactionCount(from, DefaultBlockParameterName.PENDING).send())
                .getTransactionCount();
        BigInteger baseFee = rpc(() -> web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false).send())
                .getBlock().getBaseFeePerGas();
        BigInteger tip = priorityFee();
        BigInteger maxFee = baseFee.multiply(BigInteger.TWO).add(tip);

        long id = chainId();
        RawTransaction raw = RawTransaction.createTransaction(id, nonce, gasLimit, to, BigInteger.ZERO, data, tip, maxFee);
        String signed = Numeric.toHexString(TransactionEncoder.signMessage(raw, id, relayer));
        EthSendTransaction sent = rpc(() -> web3j.ethSendRawTransaction(signed).send(), false);
        if (sent.hasError()) {
            String message = sent.getError().getMessage();
            if (message != null && message.toLowerCase().contains("insufficient funds")) {
                throw new LedgerException(HttpStatus.SERVICE_UNAVAILABLE, "RelayerWithoutGas",
                        "El relayer no tiene SepoliaETH para pagar el gas: envíale un poco a " + from);
            }
            throw new LedgerException(HttpStatus.BAD_GATEWAY, "SendFailed", message);
        }
        return sent.getTransactionHash();
    }

    private BigInteger priorityFee() {
        try {
            return web3j.ethMaxPriorityFeePerGas().send().getMaxPriorityFeePerGas();
        } catch (Exception e) {
            return Convert.toWei("1", Convert.Unit.GWEI).toBigInteger();
        }
    }

    private TransactionReceipt waitForReceipt(String txHash) {
        for (int i = 0; i < 120; i++) {
            TransactionReceipt receipt = receipt(txHash);
            if (receipt != null) {
                return receipt;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new LedgerException(HttpStatus.GATEWAY_TIMEOUT, "Timeout",
                "La transacción " + txHash + " sigue pendiente; consulta GET /ledger/tx/" + txHash);
    }

    // ---------- utilidades ----------

    /** "0x" + 64 hex se usa tal cual; cualquier otro texto se convierte con sha256(texto). Igual que en la app. */
    public static byte[] toBytes32(String value) {
        if (value == null || value.isBlank()) {
            throw new LedgerException(HttpStatus.BAD_REQUEST, "BadRequest", "Falta un identificador");
        }
        if (BYTES32.matcher(value).matches()) {
            return Numeric.hexStringToByteArray(value);
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static byte[] hex32(String value, String field) {
        if (value == null || !BYTES32.matcher(value).matches()) {
            throw new LedgerException(HttpStatus.BAD_REQUEST, "BadRequest", field + " debe ser 0x + 64 hex");
        }
        return Numeric.hexStringToByteArray(value);
    }

    private Path savedAddressFile() {
        return dataDir.resolve("device-key-ledger-" + chainId() + ".address");
    }

    private static String loadBytecode() {
        try (InputStream in = DeviceKeyLedgerClient.class.getResourceAsStream("/contracts/DeviceKeyLedger.json")) {
            JsonNode artifact = new ObjectMapper().readTree(in);
            return artifact.get("bytecode").asString();
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Falta contracts/DeviceKeyLedger.json (npm run export:artifacts)", e);
        }
    }

    private interface RpcCall<T> {
        T send() throws IOException;
    }

    private static <T extends Response<?>> T rpc(RpcCall<T> call) {
        return rpc(call, true);
    }

    private static <T extends Response<?>> T rpc(RpcCall<T> call, boolean failOnError) {
        T response;
        try {
            response = call.send();
        } catch (IOException e) {
            throw new LedgerException(HttpStatus.BAD_GATEWAY, "RpcUnavailable",
                    "No se pudo contactar con el nodo de la blockchain: " + e.getMessage());
        }
        if (failOnError && response.hasError()) {
            throw new LedgerException(HttpStatus.BAD_GATEWAY, "RpcError", response.getError().getMessage());
        }
        return response;
    }
}
