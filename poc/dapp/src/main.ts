import {
  type Address,
  type EIP1193Provider,
  type Hash,
  type Hex,
  BaseError,
  ContractFunctionRevertedError,
  createPublicClient,
  createWalletClient,
  custom,
  getAddress,
  isAddress,
  keccak256,
  parseEther,
  parseEventLogs,
  toHex,
  verifyMessage,
} from "viem";
import { sepolia } from "viem/chains";

import artifact from "./FamilyLedger.artifact.json";
import { findMetaMask } from "./eip6963";
import { familyLedgerAbi } from "./ledger";

const BACKEND_URL = "http://localhost:8080";

const $ = <T extends HTMLElement>(id: string) => document.getElementById(id) as T;
const logEl = $("log");

function log(msg: string) {
  const t = new Date().toISOString().slice(11, 23);
  logEl.textContent += `[${t}] ${msg}\n`;
  logEl.scrollTop = logEl.scrollHeight;
}

// Mide cuánto tarda cada operación (dato para la evaluación: latencia percibida).
async function timed<T>(label: string, fn: () => Promise<T>): Promise<T> {
  const start = performance.now();
  try {
    const result = await fn();
    log(`${label}: OK en ${Math.round(performance.now() - start)} ms`);
    return result;
  } catch (err) {
    log(`${label}: ERROR tras ${Math.round(performance.now() - start)} ms → ${describeError(err)}`);
    throw err;
  }
}

function describeError(err: unknown): string {
  if (err instanceof BaseError) {
    const revert = err.walk((e) => e instanceof ContractFunctionRevertedError);
    if (revert instanceof ContractFunctionRevertedError) {
      return `revert ${revert.data?.errorName ?? ""}(${(revert.data?.args ?? []).join(", ")})`;
    }
    return err.shortMessage;
  }
  // 4001 = el usuario rechazó la petición en MetaMask (EIP-1193)
  const code = (err as { code?: number }).code;
  if (code === 4001) return "rechazado por el usuario en MetaMask";
  return String((err as Error).message ?? err);
}

let provider: EIP1193Provider | undefined;
let account: Address | undefined;

const walletClient = () => {
  if (!provider || !account) throw new Error("Conecta MetaMask primero");
  return createWalletClient({ account, chain: sepolia, transport: custom(provider) });
};
// Lecturas a través del RPC de MetaMask: la PoC no necesita clave de Infura/Alchemy.
const publicClient = () => {
  if (!provider) throw new Error("Conecta MetaMask primero");
  return createPublicClient({ chain: sepolia, transport: custom(provider) });
};

function render(chainId?: string) {
  $("account").textContent = account ?? "—";
  if (chainId) {
    const n = Number(chainId);
    $("chain").textContent = `${n}${n === sepolia.id ? " (Sepolia)" : " (¡no es Sepolia!)"}`;
  }
}

async function init() {
  const mm = await findMetaMask();
  const injected = (window as { ethereum?: EIP1193Provider }).ethereum;
  if (mm) {
    provider = mm.provider;
    $("wallet").textContent = `${mm.info.name} (${mm.info.rdns}) vía EIP-6963`;
  } else if (injected) {
    provider = injected;
    $("wallet").textContent = "window.ethereum (sin EIP-6963)";
  } else {
    $("wallet").textContent = "No se ha encontrado MetaMask";
    return;
  }
  const p = provider;

  p.on("accountsChanged", (accounts) => {
    account = accounts[0] ? getAddress(accounts[0]) : undefined;
    log(`accountsChanged → ${account ?? "(ninguna: desconectado)"}`);
    render();
  });
  p.on("chainChanged", (chainId) => {
    log(`chainChanged → ${Number(chainId)}`);
    render(chainId);
  });
  p.on("disconnect", (error) => log(`disconnect → ${error.message}`));

  // ¿Ya había permiso concedido? eth_accounts no abre ningún popup.
  const existing = await p.request({ method: "eth_accounts" });
  if (existing[0]) account = getAddress(existing[0]);
  render(await p.request({ method: "eth_chainId" }));
}

$("connect").onclick = () =>
  timed("Conectar", async () => {
    if (!provider) throw new Error("MetaMask no disponible");
    const accounts = await provider.request({ method: "eth_requestAccounts" });
    account = getAddress(accounts[0]);
    render(await provider.request({ method: "eth_chainId" }));
  });

// Reabre el selector de cuentas de MetaMask. MetaMask solo expone a un sitio las cuentas
// que el usuario ha conectado a él: si cambias a una cuenta no conectada, el sitio no se entera
// y sigue firmando con la anterior.
$("choose").onclick = () =>
  timed("Elegir cuentas", async () => {
    if (!provider) throw new Error("MetaMask no disponible");
    await provider.request({ method: "wallet_requestPermissions", params: [{ eth_accounts: {} }] });
    const accounts = await provider.request({ method: "eth_accounts" });
    account = accounts[0] ? getAddress(accounts[0]) : undefined;
    log(`cuentas conectadas a este sitio: ${accounts.map((a) => getAddress(a)).join(", ")}`);
    render();
  });

$("disconnect").onclick = () =>
  timed("Desconectar", async () => {
    if (!provider) return;
    // Revoca el permiso eth_accounts de este origen en MetaMask (no solo "olvida" en la UI).
    await provider.request({ method: "wallet_revokePermissions", params: [{ eth_accounts: {} }] });
    account = undefined;
    render();
  });

$("sepolia").onclick = () =>
  timed("Cambiar a Sepolia", async () => {
    await walletClient().switchChain({ id: sepolia.id });
  });

$("signLocal").onclick = () =>
  timed("Firma local", async () => {
    const message = `Hola desde la PoC del TFG\nFecha: ${new Date().toISOString()}`;
    const signature = await walletClient().signMessage({ message }); // personal_sign
    const valid = await verifyMessage({ address: account!, message, signature });
    log(`firma=${signature.slice(0, 20)}… válida=${valid}`);
  });

$("signBackend").onclick = () =>
  timed("Login con backend", async () => {
    const challenge = await fetch(`${BACKEND_URL}/auth/challenge`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ address: account }),
    }).then((r) => r.json() as Promise<{ nonce: string; message: string }>);
    const signature = await walletClient().signMessage({ message: challenge.message });
    const res = await fetch(`${BACKEND_URL}/auth/verify`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ nonce: challenge.nonce, signature }),
    });
    log(`backend → HTTP ${res.status} ${await res.text()}`);
  });

async function waitAndReport(hash: Hash) {
  log(`tx enviada: https://sepolia.etherscan.io/tx/${hash}`);
  const receipt = await publicClient().waitForTransactionReceipt({ hash });
  log(`bloque ${receipt.blockNumber}, estado=${receipt.status}, gasUsed=${receipt.gasUsed}, precio=${receipt.effectiveGasPrice} wei`);
  for (const ev of parseEventLogs({ abi: familyLedgerAbi, logs: receipt.logs })) {
    log(`evento ${ev.eventName} ${JSON.stringify(ev.args, (_, v) => (typeof v === "bigint" ? v.toString() : v))}`);
  }
  return receipt;
}

$("send").onclick = () =>
  timed("Transferencia", async () => {
    const to = $<HTMLInputElement>("to").value.trim();
    if (!isAddress(to)) throw new Error("Dirección destino no válida");
    const hash = await walletClient().sendTransaction({ to, value: parseEther("0.0001") });
    await waitAndReport(hash);
  });

const CONTRACT_KEY = "poc.familyLedger.address";

// Despliegue firmado en MetaMask: la clave privada nunca sale de la wallet.
$("deploy").onclick = () =>
  timed("Despliegue", async () => {
    const hash = await walletClient().deployContract({
      abi: familyLedgerAbi,
      bytecode: artifact.bytecode as Hex,
    });
    const receipt = await waitAndReport(hash);
    if (!receipt.contractAddress) throw new Error("El recibo no trae dirección de contrato");
    $<HTMLInputElement>("contract").value = receipt.contractAddress;
    try {
      localStorage.setItem(CONTRACT_KEY, receipt.contractAddress);
    } catch {
      // sin almacenamiento local: el usuario puede copiar la dirección del log
    }
    log(`contrato desplegado en ${receipt.contractAddress} → https://sepolia.etherscan.io/address/${receipt.contractAddress}`);
  });

try {
  const saved = localStorage.getItem(CONTRACT_KEY);
  if (saved) $<HTMLInputElement>("contract").value = saved;
} catch {
  // ignorado
}

const contractAddress = (): Address => {
  const a = $<HTMLInputElement>("contract").value.trim();
  if (!isAddress(a)) throw new Error("Dirección de contrato no válida");
  return a;
};
const childAddress = (): Address => {
  const a = $<HTMLInputElement>("child").value.trim();
  if (!isAddress(a)) throw new Error("Dirección del hijo no válida");
  return a;
};
// El id real de la tarea vive en PostgreSQL; on-chain solo su hash (ningún dato personal).
const taskId = () => keccak256(toHex($<HTMLInputElement>("task").value.trim() || "task-demo"));

$("read").onclick = () =>
  timed("Leer contrato", async () => {
    const pc = publicClient();
    const address = contractAddress();
    const parent = await pc.readContract({ address, abi: familyLedgerAbi, functionName: "parent" });
    log(`parent=${parent}`);
    const child = $<HTMLInputElement>("child").value.trim();
    if (isAddress(child)) {
      const [isChild, balance] = await Promise.all([
        pc.readContract({ address, abi: familyLedgerAbi, functionName: "isChild", args: [child] }),
        pc.readContract({ address, abi: familyLedgerAbi, functionName: "balanceOf", args: [child] }),
      ]);
      log(`isChild=${isChild} balance=${balance}`);
    }
  });

async function roleOf(address: Address): Promise<string> {
  const pc = publicClient();
  const contract = contractAddress();
  const [parent, isChild] = await Promise.all([
    pc.readContract({ address: contract, abi: familyLedgerAbi, functionName: "parent" }),
    pc.readContract({ address: contract, abi: familyLedgerAbi, functionName: "isChild", args: [address] }),
  ]);
  if (parent.toLowerCase() === address.toLowerCase()) return "padre";
  return isChild ? "hijo" : "ninguno";
}

// simulateContract ejecuta la llamada en seco: si el contrato va a revertir (p. ej. NotParent
// o TaskAlreadyRewarded) lo sabemos ANTES de pedir la firma y sin gastar gas.
async function write(
  functionName: "addChild" | "reward" | "spend",
  args: readonly unknown[],
) {
  // Relee la cuenta activa justo antes de firmar, por si el evento accountsChanged no llegó.
  const [current] = await provider!.request({ method: "eth_accounts" });
  if (!current) throw new Error("No hay ninguna cuenta conectada");
  account = getAddress(current);
  render();
  log(`${functionName} se firmará con ${account} (rol en el contrato: ${await roleOf(account)})`);

  const { request } = await publicClient().simulateContract({
    account: account!,
    address: contractAddress(),
    abi: familyLedgerAbi,
    functionName,
    args: args as never,
  });
  const hash = await walletClient().writeContract(request);
  await waitAndReport(hash);
}

$("addChild").onclick = () => timed("addChild", () => write("addChild", [childAddress()]));
$("reward").onclick = () => timed("reward", () => write("reward", [childAddress(), taskId(), 10n]));
$("spend").onclick = () => timed("spend", () => write("spend", [keccak256(toHex("concepto-demo")), 3n]));

init().catch((e) => log(`init: ${describeError(e)}`));
