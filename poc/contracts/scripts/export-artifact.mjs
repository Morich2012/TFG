// Copia la ABI y el bytecode compilados a quien los necesita:
// - FamilyLedger a la dApp, para desplegarlo directamente desde MetaMask.
// - DeviceKeyLedger al backend, para que el relayer lo despliegue y lo llame (PoC 2).
// Uso: npm run export:artifacts  (compila y copia)
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";

const exports = [
  ["FamilyLedger", "../../dapp/src/FamilyLedger.artifact.json"],
  ["DeviceKeyLedger", "../../backend/src/main/resources/contracts/DeviceKeyLedger.json"],
];

for (const [name, path] of exports) {
  const source = new URL(`../artifacts/contracts/${name}.sol/${name}.json`, import.meta.url);
  const target = new URL(path, import.meta.url);
  const { contractName, abi, bytecode } = JSON.parse(readFileSync(source, "utf8"));
  mkdirSync(new URL(".", target), { recursive: true });
  writeFileSync(target, JSON.stringify({ contractName, abi, bytecode }, null, 2) + "\n");
  console.log(`Exportado ${contractName} → ${path.replace("../../", "")}`);
}
