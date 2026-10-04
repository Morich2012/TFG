# PoC MetaMask

Proyectos independientes. El plan completo, con la justificación de cada decisión,
está en [`docs/plan-poc-metamask.md`](../docs/plan-poc-metamask.md).

```
poc/
├── contracts/   Hardhat 3 + Solidity: FamilyLedger.sol, tests y despliegue
├── backend/       Spring Boot 4.1 + Web3j: login con firma de MetaMask y relayer de la PoC 2
├── dapp/          Vite + TypeScript + viem: página con los niveles 1, 2 y 3
├── device-key/    PoC 2: firmador Java P-256 (la misma lógica que la app Android)
└── android-child/ PoC 2: app Android en Java del hijo, gasta con la huella
```

La PoC 2 (sin MetaMask para el niño) está explicada en
[`docs/alternativas-sin-metamask.md`](../docs/alternativas-sin-metamask.md).

## Requisitos

- Node.js 22.13 o superior (`node -v`)
- Java 21 y Maven 3.9 (`java -version`, `mvn -v`)
- Navegador con la extensión MetaMask, cuentas `Padre`, `Hijo` y `Extraño`, red Sepolia y algo de SepoliaETH

## 1. Contrato (`contracts/`)

```bash
cd poc/contracts
npm install
npx hardhat test                      # 16 tests (FamilyLedger + DeviceKeyLedger)
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

Si cambias el contrato: `npm run export:artifacts` recompila y copia la ABI y el bytecode a la dApp y al backend
(y actualiza la ABI de `dapp/src/ledger.ts` a mano si cambian las funciones).

## 2. Backend (`backend/`)

```bash
cd poc/backend
mvn test                # 8 tests (firmas, anti-replay y errores del contrato)
mvn spring-boot:run     # http://localhost:8080
```

Endpoints de MetaMask: `POST /auth/challenge {address}` → `{nonce, message}` y
`POST /auth/verify {nonce, signature}` → `200 {valid:true}` / `401` / `400`.

Endpoints del relayer (PoC 2), todos bajo `/ledger`: `GET` (estado), `POST /deploy`,
`GET /children/{id}`, `POST /children/{id}/device {x, y}`, `POST /children/{id}/rewards {task, amount}`,
`POST /children/{id}/spends {conceptId, amount, r, s}` y `GET /tx/{hash}`.
Se configuran con `SEPOLIA_RPC_URL` (por defecto un nodo público), `RELAYER_DATA_DIR` (por defecto `~/.tfg-poc`)
y `DEVICE_LEDGER_ADDRESS` (opcional).

Test del flujo completo del relayer contra una red local (2 tests más):
```bash
cd poc/contracts && npx hardhat node                     # en otra terminal
cd poc/backend && LOCAL_RPC_URL=http://127.0.0.1:8545 mvn test
```

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
4. **Nivel 3b:** pulsa antes *Elegir cuentas* y marca **Padre, Hijo y Extraño** (MetaMask solo deja firmar a la dApp con cuentas conectadas a este sitio; si no, al cambiar de cuenta la dApp sigue firmando con la anterior). Con la cuenta `Padre`, *Desplegar FamilyLedger*; luego `addChild` (dirección de `Hijo`), `reward 10` con `task-1` y otra vez `reward` (debe fallar con `TaskAlreadyRewarded`). Cambia a `Extraño` y prueba `reward` (debe fallar con `NotParent`). Cambia a `Hijo` y prueba `spend 3`. *Leer estado* debe dar `balance=7`.

Cada operación queda en el registro de la página con su tiempo, su hash y el enlace a Etherscan:
cópialo como evidencia para la checklist del plan.

## 4. PoC 2: firma con la clave del móvil (`device-key/`)

```bash
cd poc/device-key
mvn test                # firma P-256 con s baja y codificación igual a abi.encode
# Regenera la firma Java que usa el test de interoperabilidad del contrato:
mvn -q compile exec:java -Dexec.args="../contracts/test/fixtures/java-device-signature.json"
```

## 5. PoC 2: app Android con huella (`android-child/`)

Guía paso a paso en [`android-child/README.md`](android-child/README.md): backend con el relayer,
despliegue de `DeviceKeyLedger` en Sepolia y prueba en el emulador o en un móvil real.
