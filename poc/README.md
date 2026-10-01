# PoC MetaMask

Tres proyectos independientes. El plan completo, con la justificación de cada decisión,
está en [`docs/plan-poc-metamask.md`](../docs/plan-poc-metamask.md).

```
poc/
├── contracts/   Hardhat 3 + Solidity: FamilyLedger.sol, tests y despliegue
├── backend/     Spring Boot 4.1 + Web3j: login con firma de MetaMask
└── dapp/        Vite + TypeScript + viem: página con los niveles 1, 2 y 3
```

## Requisitos

- Node.js 22.13 o superior (`node -v`)
- Java 21 y Maven 3.9 (`java -version`, `mvn -v`)
- Navegador con la extensión MetaMask, cuentas `Padre`, `Hijo` y `Extraño`, red Sepolia y algo de SepoliaETH

## 1. Contrato (`contracts/`)

```bash
cd poc/contracts
npm install
npx hardhat test                      # 7 tests en la red simulada
npx hardhat test nodejs --gas-stats   # gas por función
```

Despliegue en Sepolia, dos opciones:

- **A (recomendada): desde la dApp con MetaMask.** Botón *Desplegar FamilyLedger*. La clave privada no sale de MetaMask.
- **B: con Hardhat Ignition.** Hay que exportar la clave privada de la cuenta `Padre` (solo aceptable en testnet) y guardarla cifrada:
  ```bash
  npx hardhat keystore set SEPOLIA_RPC_URL      # URL de Infura/Alchemy para Sepolia
  npx hardhat keystore set SEPOLIA_PRIVATE_KEY
  npm run deploy:sepolia
  ```

Si cambias el contrato: `npm run export:dapp` recompila y copia la ABI y el bytecode a la dApp
(y actualiza la ABI de `dapp/src/ledger.ts` a mano si cambian las funciones).

## 2. Backend (`backend/`)

```bash
cd poc/backend
mvn test                # 6 tests (verificación de firmas y anti-replay)
mvn spring-boot:run     # http://localhost:8080
```

Endpoints: `POST /auth/challenge {address}` → `{nonce, message}` y
`POST /auth/verify {nonce, signature}` → `200 {valid:true}` / `401` / `400`.

## 3. dApp (`dapp/`)

```bash
cd poc/dapp
npm install
npm run dev             # abre http://localhost:5173 en el navegador con MetaMask
```

Orden de prueba:

1. **Nivel 1:** *Conectar*, cambia de cuenta en MetaMask (la página se actualiza sola), *Cambiar a Sepolia*, *Desconectar* y vuelve a conectar.
2. **Nivel 2:** *Firmar y verificar en el navegador* y, con el backend arrancado, *Login con backend*.
3. **Nivel 3a:** pega la dirección de `Hijo` y envía 0.0001 SepoliaETH.
4. **Nivel 3b:** con la cuenta `Padre`, *Desplegar FamilyLedger*; luego `addChild` (dirección de `Hijo`), `reward 10` con `task-1` y otra vez `reward` (debe fallar con `TaskAlreadyRewarded`). Cambia a `Extraño` y prueba `reward` (debe fallar con `NotParent`). Cambia a `Hijo` y prueba `spend 3`. *Leer estado* debe dar `balance=7`.

Cada operación queda en el registro de la página con su tiempo, su hash y el enlace a Etherscan:
cópialo como evidencia para la checklist del plan.
