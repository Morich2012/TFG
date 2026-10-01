# Cómo funciona la PoC (y qué nos dice)

*1 de octubre de 2026 · Complementa a [plan-poc-metamask.md](plan-poc-metamask.md) y a [evidencias-poc.md](evidencias-poc.md).*

---

## 1. Tus dos dudas, respondidas con sinceridad

### ¿Esto lo puede usar un niño?

**Tal como está, no.** Y es justo lo que la PoC tenía que descubrir. En tus pruebas:

- Cada acción que escribe en la blockchain (recompensa, gasto) abrió una **ventana de MetaMask** con direcciones en hexadecimal, gas y comisiones que hay que confirmar.
- Cada escritura tardó **entre 13 y 25 segundos** en confirmarse (en una app normal con PostgreSQL serían unos 50 ms).
- Para cambiar de "persona" tuviste que conectar cuentas, cambiar de cuenta y entender por qué la dApp seguía firmando con otra. Tú, que eres ingeniero, tropezaste con eso; un niño de 10 años no lo va a superar.
- El niño necesitaría su propia frase de recuperación de 12 palabras y saldo para pagar el gas.

### ¿Hace falta tener MetaMask siempre abierto?

Hay que distinguir lectura y escritura:

| Operación | ¿Necesita MetaMask? |
|---|---|
| **Leer** (saldo, historial, si una tarea está pagada) | **No.** Se puede leer desde el backend con Web3j o desde cualquier nodo, sin wallet |
| **Escribir** (recompensar, gastar, crear un objetivo) | **Sí, y con confirmación manual cada vez.** No hace falta tener la ventana abierta, pero MetaMask salta y el usuario debe aprobar. Es una decisión de diseño: quien tiene la clave decide |
| En móvil | Cada escritura obliga a saltar a la app de MetaMask y volver, o a usar la dApp dentro del navegador de MetaMask |

**Tu intuición es correcta:** MetaMask es una mala interfaz para el día a día de un niño. Pero eso no la hace inútil para el TFG: hay que **ponerla en el sitio correcto**.

### Entonces, ¿dónde encaja MetaMask?

En el **padre**, para pocas acciones y de valor alto. Propuesta para la reunión con tu tutor:

```
NIÑO  → App Android (Java) ──REST──▶ Spring Boot + PostgreSQL
        sin wallet, sin MetaMask        tareas, solicitudes, estadísticas, logros
                                              │ lee eventos (Web3j, sin claves)
                                              ▼
PADRE → dApp web + MetaMask ──firma──▶ Smart contract en Sepolia
        pocas firmas: aprobar recompensas,     compromisos verificables:
        crear un objetivo de ahorro bloqueado  recompensas pagadas, ahorro bloqueado
```

- El niño usa una app normal y rápida: ve su saldo, pide gastar y crea objetivos. **No firma nada en la blockchain.**
- El padre, con MetaMask, **aprueba** (una firma puede aprobar varias recompensas a la vez) y **crea compromisos** que luego no puede deshacer. Por ejemplo: "estos 20 quedan bloqueados para tu objetivo hasta el 1 de marzo".
- El backend lee la blockchain para mostrársela al niño, pero **nunca tiene claves**.
- Pedagógicamente se parece a la realidad: las cuentas de menores necesitan la aprobación de un tutor legal.

Alternativas para que el niño sí firme sin MetaMask (más complejas; para "trabajo futuro" o como ampliación):
- **Clave del niño generada dentro de la app Android** (Android Keystore + Web3j). Firma sin ventanas, pero el padre tiene que recargarle gas y la recuperación de la cuenta es problema tuyo.
- **Permisos delegados** (ERC-7715 *Advanced Permissions*, Smart Accounts Kit de MetaMask): el padre concede a la app del niño un permiso limitado, por ejemplo "gastar hasta 5 a la semana". Es la solución elegante, y tus cuentas ya son *smart accounts* (ver 5.2), pero la librería es TypeScript y no se puede usar desde Android en Java.

**Lo que esto significa para la decisión:** la PoC demuestra que **es técnicamente viable** (todo funcionó en Sepolia real) y además ha medido su **coste de usabilidad**. El argumento para seguir con blockchain ya no puede ser "el niño usa una wallet", sino **"los compromisos del padre quedan registrados de forma que ni él ni el servidor pueden alterarlos"**. Si a tu tutor ese argumento no le convence, la alternativa tradicional queda bien justificada con estos mismos datos. Las dos salidas son defendibles.

---

## 2. Qué hace cada botón

El recorrido general de cualquier escritura es el mismo:

```
Botón ─▶ dApp (viem) ─▶ MetaMask (ventana: confirmas) ─▶ nodo RPC ─▶ red Sepolia
                                                                       │ un validador la mete en un bloque (~12 s)
        dApp ◀── recibo (bloque, estado, gas, eventos) ◀───────────────┘
```

### Nivel 1 · Conexión

| Botón | Qué hace por dentro | Qué viste |
|---|---|---|
| (al cargar) | Lanza el evento **EIP-6963** `eip6963:requestProvider`; MetaMask responde anunciándose con su id `io.metamask`. Así la dApp sabe qué wallets hay sin depender de `window.ethereum` | "MetaMask (io.metamask) vía EIP-6963" |
| **Conectar** | `eth_requestAccounts`: pide a MetaMask permiso para ver tus cuentas. La primera vez abre la ventana; luego responde al instante | `Conectar: OK en 24 ms` (ya tenía permiso) |
| **Elegir cuentas** | `wallet_requestPermissions`: reabre el selector de cuentas para conectar más | Conectaste Padre, Hijo y Extraño |
| (cambiar cuenta en MetaMask) | MetaMask emite el evento `accountsChanged` y la dApp actualiza la cuenta que firma | `accountsChanged → 0x111B…` |
| **Cambiar a Sepolia** | `wallet_switchEthereumChain` con el id 11155111 | — |
| **Desconectar** | `wallet_revokePermissions`: retira el permiso del sitio en MetaMask | — |

### Nivel 2 · Firma (no cuesta gas ni va a la blockchain)

| Botón | Qué hace por dentro |
|---|---|
| **Firmar y verificar en el navegador** | `personal_sign`: MetaMask firma un texto con tu clave privada (que nunca sale de MetaMask). Luego `verifyMessage` hace la operación inversa: a partir del texto y la firma **recupera la dirección** que firmó y la compara. Viste `válida=true` |
| **Login con backend** | 1) `POST /auth/challenge`: Spring Boot genera un texto con un **nonce** aleatorio. 2) MetaMask lo firma. 3) `POST /auth/verify`: Spring Boot recupera la dirección con Web3j (`Sign.signedPrefixedMessageToKey`) y comprueba que coincide. El nonce se borra al usarse, así que una firma robada no sirve dos veces. Viste `HTTP 200 {"valid":true,"address":"0x8481…"}` con las tres cuentas |

**Por qué importa:** demuestra que el backend puede saber *quién eres* sin guardar ninguna clave privada. Es la base de un "login con MetaMask" para el padre.

### Nivel 3a · Transferencia

| Botón | Qué hace por dentro |
|---|---|
| **Enviar 0.0001 SepoliaETH** | `eth_sendTransaction` con `to` y `value`. MetaMask calcula el gas, tú confirmas y la red la incluye en un bloque. `waitForTransactionReceipt` espera al recibo. Viste el bloque 11823688, 21.000 de gas (lo que cuesta siempre una transferencia simple) y 12,7 s |

### Nivel 3b · Contrato

| Botón | Qué hace por dentro |
|---|---|
| **Desplegar FamilyLedger** | Envía una transacción **sin destinatario** cuyo contenido es el *bytecode* del contrato (el Solidity compilado). La red crea el contrato en una dirección nueva y ejecuta el `constructor`, que guarda `parent = msg.sender` (la cuenta que firmó). Por eso el padre es quien despliega |
| **addChild / reward / spend** | 1) Relee qué cuenta firmará y su rol. 2) `simulateContract`: ejecuta la función **en seco** contra el estado actual, sin firmar ni pagar. Si va a fallar, falla aquí. Por eso el duplicado y el extraño fallaron en unos 1,8 s y **sin abrir MetaMask ni gastar gas**. 3) Si la simulación pasa, `writeContract` pide la firma en MetaMask. 4) Se espera el recibo y se decodifican los **eventos** (`ChildAdded`, `RewardPaid`, `Spent`) |
| **Leer estado** | `eth_call` a `parent()`, `isChild()` y `balanceOf()`. Son **lecturas gratuitas**: no hay transacción ni ventana de MetaMask, y tardaron 369 ms |

---

## 3. El contrato explicado (`poc/contracts/contracts/FamilyLedger.sol`)

```solidity
address public immutable parent;                    // quién es el padre; fijado al desplegar, no cambia nunca
mapping(address => bool) public isChild;            // como un HashMap<Address, Boolean> en Java
mapping(address => uint256) public balanceOf;       // saldo interno de cada hijo (NO es dinero ni token)
mapping(bytes32 => bool) public taskRewarded;       // tareas ya pagadas (anti-duplicado)
```

Conceptos clave, comparados con Java:

| Solidity | Equivalente mental en Java | En el contrato |
|---|---|---|
| `msg.sender` | el usuario autenticado de la petición, pero **garantizado criptográficamente** por la firma | Quién llama: así se sabe si es el padre o un hijo |
| `modifier onlyParent` | una anotación tipo `@PreAuthorize("hasRole('PARENT')")` | Se ejecuta antes de la función; si no eres el padre, `revert NotParent()` |
| `revert Error()` | `throw new Exception()`, **pero deshace todo** lo que hizo la transacción | Los rechazos que viste |
| `error NotParent()` | una clase de excepción propia | Errores con nombre, más baratos que mensajes de texto |
| `event RewardPaid(...)` | un log estructurado que se guarda para siempre en la blockchain | El backend lo leería para sincronizar PostgreSQL |
| `bytes32 taskId` | el hash de tu `UUID` de PostgreSQL | **Nada personal on-chain**: solo un identificador opaco |
| `immutable` | `final`, fijado en el constructor | `parent` |

Orden de las comprobaciones en `reward` (importa para entender tus errores):
1. `onlyParent`: ¿eres el padre? Si no, `NotParent`. **Por esto el Extraño obtuvo `NotParent`.**
2. ¿El destinatario es hijo? Si no, `NotChild`.
3. ¿Cantidad > 0? Si no, `ZeroAmount`.
4. ¿Tarea ya pagada? Si sí, `TaskAlreadyRewarded`. **Por esto el segundo `reward` falló.**
5. Marca la tarea, suma el saldo y emite `RewardPaid`.

Antes del arreglo, el Extraño recibía `TaskAlreadyRewarded` porque en realidad firmaba el Padre: pasaba el paso 1 y caía en el 4.

---

## 4. Cómo se han hecho los tests

### 4.1 Tests del contrato (`poc/contracts/test/FamilyLedger.ts`, `npx hardhat test`)

- Hardhat levanta una **blockchain simulada en memoria** con 20 cuentas de prueba con saldo. No hay red ni MetaMask, y cada test dura milisegundos.
- `viem.deployContract("FamilyLedger")` despliega un contrato nuevo en cada test, así ninguno depende de otro.
- `{ account: stranger.account }` hace que una llamada la firme otra cuenta. Así se simula al Extraño o al Hijo.
- Las aserciones `viem.assertions.revertWithCustomError(..., "NotParent")` comprueban que la llamada **falla con ese error exacto**, y `emitWithArgs` que se emitió el evento con esos valores.
- Los 7 tests cubren: padre fijado, recompensa con evento, rechazo del extraño, rechazo del duplicado, rechazo de un no-hijo, gasto con saldo insuficiente y que el padre no puede gastar por el hijo. **Es lo mismo que hiciste a mano, pero automático y repetible.** Para la memoria es tu evidencia de "corrección y control de acceso".

### 4.2 Tests del backend (`poc/backend`, `mvn test`)

- `SignatureVerifierTest`: una firma generada en JavaScript (igual a la de MetaMask) **verificada en Java**. Así se prueba que los dos mundos son compatibles. También comprueba que se rechaza si cambia una letra del mensaje, si la cuenta es otra o si la firma está mal formada.
- `AuthControllerTest`: el flujo reto, firma y verificación completo, más el **ataque de repetición** (reutilizar la firma da 400).

### 4.3 Prueba de extremo a extremo (la hice yo antes de dártela)

Abrí la dApp en un Chromium automático (Playwright) con una **MetaMask falsa** que se anuncia por EIP-6963 y reenvía las peticiones a un nodo local de Hardhat configurado con el id de Sepolia. Luego "pulsé" todos los botones por código. Sirvió para entregarte algo que funcionaba, y para reproducir y corregir tu fallo de las cuentas antes de pedirte que lo repitieras. **No sustituye a tu prueba real**, que es la evidencia que cuenta.

---

## 5. Lo que tus pruebas han medido (datos para la memoria)

### 5.1 Latencia

| Operación | Tiempo |
|---|---|
| Leer el contrato | **0,37 s** |
| Firma (sin blockchain), incluido tu clic | 1,3 – 4,8 s |
| Login con el backend | 1,3 – 2,4 s |
| Rechazo detectado por simulación | ~1,8 s, sin gas |
| Transferencia | 12,7 s |
| `addChild` / `reward` | 13 – 15 s |
| `spend` (Hijo) | 22 s |
| Despliegue | 25 s |

### 5.2 Gas: local vs. Sepolia real

| Operación | Local (cuenta normal) | Sepolia (tu cuenta) | Diferencia |
|---|---|---|---|
| Despliegue | 373.968 | 373.968 | = |
| `addChild` | 45.378 | 135.124 | **+89.746** |
| `reward` | 71.275 | 161.057 | **+89.782** |
| `spend` | 31.270 | 133.534 | **+102.264** |

**Hallazgo:** tus cuentas de MetaMask están convertidas en **smart accounts (EIP-7702)**. Las llamadas al contrato no van directas: pasan por el `DelegationManager` de MetaMask (`redeemDelegations`, comprobado en Etherscan en la transacción de `spend`), y eso añade **unos 90.000-100.000 de gas por operación**, es decir, la triplica. En Sepolia da igual (0,00034 SepoliaETH por `spend`), pero en una red real es dinero. Para la evaluación del TFG conviene medir las dos variantes: MetaMask permite volver a "cuenta estándar" desde los detalles de la cuenta (compruébalo en tu versión). Es una comparación propia y original.
