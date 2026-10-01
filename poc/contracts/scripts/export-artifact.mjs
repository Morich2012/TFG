// Copia la ABI y el bytecode compilados de FamilyLedger a la dApp,
// para poder desplegar el contrato directamente desde MetaMask.
// Uso: npm run export:dapp  (compila y copia)
import { readFileSync, writeFileSync } from "node:fs";

const source = new URL("../artifacts/contracts/FamilyLedger.sol/FamilyLedger.json", import.meta.url);
const target = new URL("../../dapp/src/FamilyLedger.artifact.json", import.meta.url);

const artifact = JSON.parse(readFileSync(source, "utf8"));
const { contractName, abi, bytecode } = artifact;
writeFileSync(target, JSON.stringify({ contractName, abi, bytecode }, null, 2) + "\n");
console.log(`Exportado ${contractName} → dapp/src/FamilyLedger.artifact.json`);
