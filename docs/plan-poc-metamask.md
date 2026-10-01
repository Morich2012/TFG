# Plan de la PoC de MetaMask (TFG)

*Versión 1 · 30 de septiembre de 2026 · Todas las versiones y herramientas están comprobadas hoy contra la documentación oficial (fuentes al final).*

Objetivo de la PoC: reunir evidencia suficiente para decidir con el tutor, antes del 9 de octubre, si el TFG sigue la vía blockchain/MetaMask o vuelve a una arquitectura tradicional. La PoC **no** es la aplicación final.

---

## 0. Lo más importante antes de empezar (hallazgos críticos)

Al revisar la documentación actual han salido tres cosas que cambian el planteamiento. Mejor saberlas ya que descubrirlas el día 8.

1. **El SDK nativo de MetaMask para Android está archivado (26 feb 2026).** El repositorio `MetaMask/metamask-android-sdk` es de solo lectura y su última versión es la 0.6.6 (octubre 2024). Su sustituto, **MetaMask Connect** (`@metamask/connect-evm`, v2.1.1), soporta JavaScript web, React Native y Node.js, **pero no Android nativo en Java/Kotlin**. Conclusión: *no existe hoy una forma oficial de que una app Android escrita en Java hable directamente con MetaMask.* Las alternativas reales para móvil son:
   - abrir una dApp web dentro del navegador integrado de MetaMask Mobile mediante un *deeplink* (`https://link.metamask.io/dapp/<url>`), que es lo que probaremos;
   - hacer el cliente móvil en React Native (con `@metamask/connect-evm`), lo que te obliga a cambiar de Java a TypeScript;
   - WalletConnect/Reown AppKit para Kotlin (protocolo genérico de wallets, no específico de MetaMask; lo dejo como *pendiente de verificar* si llegamos a la fase de arquitectura);
   - **MetaMask Embedded Wallets** (antes Web3Auth), que sí tiene SDK Android (v10.0.1), pero es otro modelo: login social y claves gestionadas por un proveedor externo, no la extensión MetaMask del usuario, y requiere un *client ID* de su dashboard.

   **Implicación:** la PoC debe comprobar el camino web y además el camino móvil, porque el riesgo de viabilidad está en el móvil, no en la conexión de escritorio (que funciona casi seguro).

2. **Sepolia sigue siendo la testnet recomendada para aplicaciones** (ethereum.org, actualizada el 23-09-2026), pero hay una **propuesta abierta (27-05-2026)** para sustituirla, con fin de vida estimado en **Q2/Q3 de 2027**. No afecta a la PoC. Para el TFG basta con que la red (chainId y RPC) sea configuración y no esté grabada en el código. Conviene mencionarlo en la memoria como riesgo identificado.

3. **El argumento de valor de blockchain no lo va a dar la PoC.** La PoC demuestra que es *técnicamente viable*; si aporta *valor* frente a PostgreSQL es una cuestión de diseño que hay que responder aparte (sección 12). Tu tutor pidió comprobar la viabilidad técnica, pero en la reunión te preguntará lo segundo.

---

## 1. Qué tecnologías utilizar

| Pieza | Elección | Versión comprobada | Por qué | Descartado y por qué |
|---|---|---|---|---|
| Wallet | MetaMask (extensión de navegador + app móvil Android) | — | Es lo que pidió evaluar el tutor | — |
| Red | **Sepolia** (chainId 11155111) | — | Testnet recomendada para apps; MetaMask la trae de serie | Hoodi (pensada para validadores), Ephemery (se reinicia cada mes), mainnet/L2 reales (dinero real) |
| Lenguaje de contratos | **Solidity** | compilador 0.8.34 (el que fija la plantilla de Hardhat) | Estándar de facto en EVM, lo que piden las ofertas y la bibliografía | Vyper (menos material docente) |
| Entorno de contratos | **Hardhat 3** | 3.18.0 | Tests en TypeScript con viem, despliegue declarativo (Ignition), almacén cifrado de claves (`keystore`), informe de gas integrado | Foundry: excelente, pero toda la cadena de herramientas es distinta (Rust, tests en Solidity); Hardhat comparte TypeScript y viem con la dApp, así aprendes una sola librería |
| dApp web | **Vite + TypeScript + viem**, sin framework | Vite 8.3.1, viem 2.57.1 | Mínimo imprescindible; viem es la librería que usan Hardhat 3 y los ejemplos de MetaMask | React/wagmi: añaden capas que no aportan nada para 4 botones. ethers v6 sigue vivo, pero usar viem en todo evita dos APIs |
| Conexión con MetaMask | **EIP-1193 + EIP-6963 directo** (niveles 1-3) | — | Es el estándar sobre el que se construye todo; no necesita clave de Infura; aprendes lo que pasa realmente | `@metamask/connect-evm` exige una API key de Infura. Lo reservamos para la prueba móvil opcional (sección 11, prueba M3) |
| Backend | **Spring Boot 4.1.1 + Java 21 + Web3j 6.0.0** (solo el módulo `org.web3j:crypto`) | Web3j 6.0.0 publicado en Maven Central en junio de 2026, Java 21 | Tu terreno. El backend **solo verifica firmas**, no guarda ninguna clave privada | Que el backend firme transacciones en nombre de usuarios (justo lo que quieres evitar) |
| Explorador | sepolia.etherscan.io | — | Ver las transacciones y el contrato | — |

**Sobre Web3j:** sí tiene sentido, pero para lo justo: verificar firmas (nivel 2) y, más adelante, *leer* eventos del contrato para sincronizarlos con PostgreSQL. No lo usaría para que el backend envíe transacciones de usuarios. Está mantenido (Linux Foundation Decentralized Trust) y requiere Java 21.

---

## 2. Qué necesitas instalar

| Herramienta | Versión mínima | Para qué | Comprobar con |
|---|---|---|---|
| Node.js (LTS) | **22.13** o superior (lo exige Hardhat 3) | Contratos y dApp | `node -v` |
| npm | la que trae Node | — | `npm -v` |
| JDK | **21** (lo exige Web3j 6 y encaja con Spring Boot 4) | Backend | `java -version` |
| Maven | 3.9 | Backend | `mvn -v` |
| Git | cualquiera reciente | Repositorio | `git --version` |
| VS Code + extensión **"Solidity" de Nomic Foundation** | — | Resaltado y errores de Solidity | — |
| Chrome, Brave o Firefox **con un perfil nuevo** | — | Aislar la wallet de pruebas | — |
| Extensión MetaMask | la actual, **solo desde metamask.io** | Nivel 1-3 | — |
| MetaMask Mobile en tu Android | la actual, desde Google Play | Prueba móvil | — |

Cuentas externas (gratuitas): una API key de un proveedor RPC para desplegar desde Hardhat (Infura o Alchemy; también sirve un RPC público de Sepolia, aunque es menos fiable) y, opcionalmente, una API key de Etherscan para verificar el código del contrato.

---

## 3. Qué red de prueba utilizar

**Sepolia**, con el nodo local de Hardhat para el día a día:

- **Hardhat simulado (local):** tests y desarrollo. Instantáneo, gratis y reproducible. Todos los tests de la PoC corren aquí.
- **Sepolia:** la prueba "real". Solo para demostrar que MetaMask firma y la transacción se incluye en una red pública, y para medir latencia y gas reales.

**Conseguir SepoliaETH (faucets):** Google Cloud Web3 Faucet (pide iniciar sesión con Google), y los faucets de Alchemy o Infura. Los requisitos de los faucets cambian a menudo (algunos piden saldo en mainnet para evitar bots), así que comprueba el día que lo uses. **Necesitas poco:** el despliegue del contrato consume unos 690.000 de gas y cada operación entre 30.000 y 72.000 (medido, sección 11). Con 0,05 SepoliaETH vas sobrado.

---

## 4. Cómo crear y configurar la wallet

Reglas de seguridad (esto también va a la memoria):

1. **Perfil de navegador nuevo** solo para el TFG. Nunca uses una wallet con fondos reales.
2. Instala MetaMask desde metamask.io y **crea una wallet nueva**. Apunta la frase de recuperación (12 palabras) en papel. Aunque sea de pruebas, no la subas a GitHub ni la pegues en chats.
3. Crea **tres cuentas** dentro de MetaMask y renómbralas:
   - `Padre`: desplegará el contrato y será el `parent`.
   - `Hijo`: recibirá recompensas y gastará.
   - `Extraño`: para demostrar que el contrato **rechaza** operaciones no autorizadas.
4. Activa "Mostrar redes de prueba" y selecciona **Sepolia**.
5. Pide SepoliaETH para `Padre` (y un poco para `Hijo`, que también paga gas al gastar).
6. Para desplegar desde Hardhat necesitas la clave privada de `Padre` (MetaMask → detalles de la cuenta → mostrar clave privada). **Esto solo es aceptable porque es una cuenta de testnet.** Se guarda cifrada con el keystore de Hardhat, nunca en el código ni en un `.env` subido al repositorio:
   ```bash
   npx hardhat keystore set SEPOLIA_RPC_URL
   npx hardhat keystore set SEPOLIA_PRIVATE_KEY
   ```

---

## 5. Cómo crear la dApp mínima

El código ya está escrito, probado y subido al repositorio en [`poc/`](../poc/) (instrucciones en [`poc/README.md`](../poc/README.md)):

```
poc/
├── contracts/   Hardhat 3: FamilyLedger.sol, tests, módulo de despliegue
├── backend/     Spring Boot 4.1: /auth/challenge y /auth/verify (Web3j)
└── dapp/        Vite + TS + viem: una página con los niveles 1, 2 y 3
```

Arranque:

```bash
# 1) Contratos
cd poc/contracts
npm install
npx hardhat test                      # 7 tests en local
npx hardhat test nodejs --gas-stats   # tabla de gas por función

# 2) Backend (otra terminal)
cd poc/backend
mvn test                              # 6 tests
mvn spring-boot:run                   # http://localhost:8080

# 3) dApp (otra terminal)
cd poc/dapp
npm install
npm run dev                           # abre http://localhost:5173 en el perfil con MetaMask
```

Qué es cada fichero de la dApp:
- `src/eip6963.ts`: descubre las wallets instaladas mediante EIP-6963 y elige MetaMask por su identificador (`io.metamask`). Es la forma actual de hacerlo; `window.ethereum` queda solo como respaldo.
- `src/ledger.ts`: la ABI del contrato (la "interfaz" que la dApp usa para llamarlo).
- `src/main.ts`: la lógica de los botones. Cada operación registra en pantalla cuánto ha tardado, lo que te da datos de latencia para la evaluación.

---

## 6. Cómo conectar MetaMask (nivel 1)

Lo que hace la dApp (en `src/main.ts`):

| Acción | Método EIP-1193 | Qué comprobar |
|---|---|---|
| Detectar la wallet | evento `eip6963:announceProvider` | Aparece "MetaMask (io.metamask) vía EIP-6963" |
| Saber si ya había permiso (sin popup) | `eth_accounts` | Al recargar, si ya estabas conectado, se muestra la cuenta |
| Conectar | `eth_requestAccounts` | MetaMask abre el popup de conexión |
| Detectar cambio de cuenta | evento `accountsChanged` | Cambia de `Padre` a `Hijo` en MetaMask y la página se actualiza sola |
| Detectar cambio de red | evento `chainChanged` | Aparece "¡no es Sepolia!" si cambias de red |
| Cambiar a Sepolia | `wallet_switchEthereumChain` (vía `switchChain` de viem) | MetaMask pide confirmación |
| Desconectar | `wallet_revokePermissions` | Revoca de verdad el permiso del sitio en MetaMask, no solo en la interfaz |
| Reconectar | `eth_requestAccounts` otra vez | Vuelve a pedir permiso |

**Matiz conceptual (útil para la memoria):** "desconectar" no existe en el estándar original EIP-1193; la dApp no controla la wallet. Lo más limpio es revocar el permiso con `wallet_revokePermissions`, que MetaMask soporta.

---

## 7. Cómo obtener la cuenta

- `eth_requestAccounts` devuelve un array; `accounts[0]` es la cuenta seleccionada en MetaMask.
- La dApp la normaliza con `getAddress` (formato *checksum* EIP-55).
- Nunca confíes en la cuenta que dice el cliente para nada importante en el backend: para eso existe el nivel 2.

---

## 8. Cómo firmar un mensaje (nivel 2)

Dos pruebas, las dos en la dApp:

**8.1 Firmar y verificar en el navegador** (botón *Firmar y verificar en el navegador*): `signMessage` de viem llama a `personal_sign` (EIP-191); `verifyMessage` recupera la dirección y la compara.

**8.2 "Iniciar sesión con MetaMask" contra Spring Boot** (botón *Login con backend*). Este es el experimento que más importa para la arquitectura, porque demuestra que **el backend puede saber quién es el usuario sin tener ninguna clave privada**:

```
dApp                                Spring Boot
 │ POST /auth/challenge {address} ─▶│ genera nonce aleatorio (16 bytes), mensaje con caducidad de 5 min
 │◀──────── {nonce, message} ───────│
 │ MetaMask: personal_sign(message) │
 │ POST /auth/verify {nonce, sig} ─▶│ Web3j recupera la dirección que firmó;
 │                                  │ el nonce se borra al usarlo (no se puede reutilizar la firma)
 │◀──── 200 {valid:true} / 401 ─────│
```

Código: `backend/src/main/java/es/upm/tfg/poc/auth/SignatureVerifier.java` (la verificación, unas 20 líneas) y `AuthController.java`.

Notas críticas:
- MetaMask recomienda `eth_signTypedData_v4` (EIP-712) para datos estructurados y `personal_sign` para autenticación. `eth_sign` está obsoleto. Para login, `personal_sign` es lo correcto. En el TFG real el mensaje debería seguir el formato **SIWE (EIP-4361)**, que MetaMask reconoce y muestra de forma especial.
- La verificación con `ecrecover` solo vale para cuentas normales (EOA). Si en el futuro usas *smart accounts*, la firma se verifica llamando al contrato de la cuenta (ERC-1271). Es un argumento más para no meter *account abstraction* sin necesidad.
- En la PoC los nonces están en memoria. En el TFG irían a PostgreSQL o Redis, y tras verificar se emitiría un JWT normal de Spring Security.

---

## 9. Cómo realizar una transacción de prueba (nivel 3a)

Botón *Enviar 0.0001 SepoliaETH*: pega la dirección de `Hijo` y envía desde `Padre`.
- `sendTransaction` hace que MetaMask muestre la confirmación con la estimación de gas.
- `waitForTransactionReceipt` espera a que la transacción se incluya en un bloque.
- El registro muestra el enlace a Etherscan, el bloque, el estado, el gas usado y el precio efectivo.

Qué medir: tiempo desde el clic hasta el recibo (en Sepolia, del orden de un bloque, unos 12 s, más lo que tardes en confirmar), gas (21.000 en una transferencia simple) y coste en SepoliaETH.

---

## 10. Cómo interactuar con un smart contract sencillo (nivel 3b)

### El contrato: `FamilyLedger.sol`

No es un token: es un **libro de recompensas familiar con saldos internos sin valor económico** (la opción 3 de tu lista; justificación en la sección 12.3). Está pensado para ejercitar justo lo que quieres evaluar:

| Función | Quién puede | Qué demuestra |
|---|---|---|
| `addChild(child)` | solo `parent` | Control de acceso por rol |
| `reward(child, taskId, amount)` | solo `parent` | Recompensa verificable. `taskId` es un **hash** de un id que vive en PostgreSQL: **ningún dato personal on-chain**. Rechaza pagar dos veces la misma tarea (`TaskAlreadyRewarded`) |
| `spend(conceptId, amount)` | solo el propio hijo | El hijo decide y firma su gasto; no puede gastar más de lo que tiene (`InsufficientBalance`); el padre no puede gastar por él |
| eventos `ChildAdded`, `RewardPaid`, `Spent` | — | Historial auditable, que el backend podría indexar |

Tests incluidos (`npx hardhat test`, **7/7 en verde**): el despliegue fija al padre, la recompensa emite el evento, se rechaza al extraño (`NotParent`), se rechaza el duplicado, se rechaza a quien no es hijo, el gasto con saldo insuficiente y que el padre gaste por el hijo.

### Pasos

**Opción A (recomendada, añadida el 1 de octubre):** desplegar desde la propia dApp con el botón *Desplegar FamilyLedger*. MetaMask firma el despliegue y la clave privada nunca sale de la wallet, sin keystore ni API key de RPC. La cuenta que despliega queda como `parent`.

**Opción B:** con Hardhat Ignition (exige exportar la clave privada de `Padre`):

```bash
cd poc/contracts
npx hardhat keystore set SEPOLIA_RPC_URL        # si no lo hiciste en el paso 4
npx hardhat keystore set SEPOLIA_PRIVATE_KEY    # clave de la cuenta Padre (solo testnet)
npx hardhat ignition deploy ignition/modules/FamilyLedger.ts --network sepolia
# Opcional: publicar el código en Etherscan
npx hardhat keystore set ETHERSCAN_API_KEY
npx hardhat ignition deploy ignition/modules/FamilyLedger.ts --network sepolia --verify
```

> Para `--verify` hay que añadir `verify: { etherscan: { apiKey: configVariable("ETHERSCAN_API_KEY") } }` a `hardhat.config.ts`. No lo he añadido para no obligarte a tener esa clave.

Luego, en la dApp:
1. Con la opción A la dirección se rellena sola; con la B, pega la que imprime Ignition.
2. Con `Padre`: `addChild` (dirección de `Hijo`) y después `reward 10` con un id de tarea, por ejemplo `task-1`.
3. Pulsa `reward` otra vez con el mismo id: **debe fallar antes de abrir MetaMask** con `revert TaskAlreadyRewarded(...)`. La dApp usa `simulateContract`, que ejecuta la llamada en seco: detecta el error sin pedir firma y sin gastar gas.
4. Cambia a `Extraño` en MetaMask e intenta `reward`: debe fallar con `NotParent`.
5. Cambia a `Hijo` y pulsa `spend 3`. Luego *Leer estado*: debe dar `balance=7`.
6. Comprueba cada transacción y sus eventos en sepolia.etherscan.io.

---

## 11. Cómo comprobar que todo funciona

### Lo que ya he comprobado yo (sin MetaMask real)
- **Contrato:** 7/7 tests en la red simulada de Hardhat.
- **Backend:** 6/6 tests, incluida una firma generada con viem (idéntica a la de MetaMask) verificada con Web3j, más el rechazo de otra cuenta, de un mensaje alterado, de una firma mal formada y de la reutilización de un nonce.
- **dApp completa de extremo a extremo** con Chromium automatizado y una wallet simulada que se anuncia como MetaMask vía EIP-6963, contra un nodo local de Hardhat con el chainId de Sepolia y el backend levantado: conexión, cambio de cuenta, firma local, login con el backend (HTTP 200), transferencia, `addChild`, `reward`, duplicado rechazado (`TaskAlreadyRewarded`), `spend` como hijo, lectura de saldo (7) y desconexión. Todo correcto.

**Lo que no he podido probar** y te toca a ti: la extensión MetaMask real, Sepolia real (faucet, latencias reales) y el móvil.

### Gas medido (red simulada, compilador sin optimizar)

| Operación | Gas |
|---|---|
| Despliegue | ~690.000 |
| `addChild` | ~45.600 |
| `reward` | ~72.000 |
| `spend` | ~31.900 |
| Transferencia de ETH | 21.000 |

### Checklist de la PoC (rellénala con evidencias: capturas y hashes de transacción)

| # | Prueba | Evidencia | ✓ |
|---|---|---|---|
| N1.1 | Detecta MetaMask vía EIP-6963 | captura | |
| N1.2 | Conecta y muestra la cuenta | captura | |
| N1.3 | Cambio de cuenta en MetaMask reflejado sin recargar | captura o vídeo | |
| N1.4 | Cambio de red detectado y vuelta a Sepolia | captura | |
| N1.5 | Desconectar (revocar) y reconectar | captura | |
| N2.1 | Firma verificada en el navegador | log | |
| N2.2 | Login con Spring Boot: 200 con la cuenta correcta | log | |
| N2.3 | Reintentar el mismo nonce da 400 (anti-replay) | `mvn test` y log | |
| N3.1 | Transferencia en Sepolia confirmada | hash de la tx | |
| N3.2 | Contrato desplegado en Sepolia | dirección y enlace a Etherscan | |
| N3.3 | `addChild` + `reward` + `spend` confirmados | hashes | |
| N3.4 | Duplicado rechazado (`TaskAlreadyRewarded`) | log | |
| N3.5 | Extraño rechazado (`NotParent`) | log | |
| M1 | La dApp abierta en el navegador de MetaMask Mobile conecta y firma | captura del móvil | |
| M2 | Una app Android mínima (Java) abre la dApp con el deeplink `https://link.metamask.io/dapp/<url>` | vídeo | |
| M3 *(opcional)* | `@metamask/connect-evm` en escritorio conecta con MetaMask Mobile por QR | captura | |

**Para M1 y M2** la dApp debe ser accesible desde el móvil. Lo más sencillo es `npm run build` y publicar `dist/` en GitHub Pages (es estática). Para el login con backend en móvil, el backend tendría que estar accesible también; si se complica, en móvil basta con N1, N2.1 y N3. La app Android de M2 es un `Activity` con un botón que lanza un `Intent` con `ACTION_VIEW` y esa URL: son 15 líneas y demuestran el flujo Android → MetaMask → blockchain que planteabas en el punto 13.

---

### Hallazgos durante la ejecución real (1 de octubre)

- **Cambio de cuenta:** MetaMask solo expone a un sitio las cuentas conectadas a él. Si el usuario cambia a una cuenta no conectada, la dApp no recibe `accountsChanged` y sigue firmando con la anterior. Se vio porque el "Extraño" obtuvo `TaskAlreadyRewarded` (firmaba como Padre) y el "Hijo" `NotChild`. Se corrigió con el botón *Elegir cuentas* (`wallet_requestPermissions`), releyendo `eth_accounts` antes de cada escritura y mostrando el rol de la cuenta que va a firmar. Es un dato de UX relevante: con varias personas en un mismo navegador la cuenta activa no es evidente.
- **La cuenta del padre es una smart account (EIP-7702):** en Etherscan la cuenta aparece como *Authority* delegada, y las llamadas al contrato salen como *Redeem Delegation*. MetaMask ha actualizado la cuenta a MetaMask Smart Account. Las llamadas al contrato llegan como transacciones internas, por eso la página del contrato en Etherscan no las lista en la pestaña principal. La firma `personal_sign` sigue siendo ECDSA de la clave original, así que la verificación del backend sigue siendo válida. Esto conviene comentarlo en la memoria.

## 12. Qué conclusiones debemos extraer de la PoC

### 12.1 Criterios de decisión (acuérdalos con el tutor antes de ejecutar la PoC, así la decisión no es subjetiva)

| Criterio | "Sigue blockchain" si… | "Recoger cable" si… |
|---|---|---|
| Viabilidad escritorio (N1-N3) | todo en verde en 1-2 días | bloqueos serios |
| Viabilidad móvil (M1-M2) | el flujo deeplink → dApp → firma funciona y es usable | no funciona o la experiencia es inaceptable |
| Coste de aprendizaje | has entendido y modificado el contrato y la dApp tú solo | dependes de copiar código sin entenderlo |
| Valor (12.3) | puedes defender al menos **una** funcionalidad que gana algo real con blockchain | todo lo on-chain lo haría igual una tabla con auditoría |
| Encaje en plazo | el alcance cabe en un semestre (12.4) | exige React Native + contratos + backend + Android a la vez |

### 12.2 Arquitectura: lo que la PoC ya permite afirmar

| Opción (tu punto 13) | Veredicto |
|---|---|
| Android (Java) → MetaMask → blockchain | **No hay SDK oficial** hoy. Solo es posible vía deeplink a una dApp web (M2) |
| Android → Spring Boot → blockchain, **firmando el backend por los usuarios** | Conceptualmente incorrecta: el backend custodia las claves y blockchain pierde su sentido. **Descartada** |
| Android → Spring Boot (lecturas e indexación) **y** dApp web/navegador de MetaMask para las firmas | **La más coherente.** Cada usuario firma con su wallet; el backend verifica firmas (N2.2), lee eventos y guarda en PostgreSQL todo lo demás |
| React Native + `@metamask/connect-evm` | Técnicamente la más limpia en móvil, pero te saca de Java y añade un stack nuevo. Solo si la usabilidad de M2 es mala |

Principio que deberías defender en la memoria: **el backend nunca firma en nombre de un usuario.** La única clave que podría justificarse en el servidor es una cuenta *relayer* propia del sistema que pague el gas, y eso sería una ampliación, no el MVP.

### 12.3 Dónde aporta valor blockchain (el análisis crítico que te pide el tutor)

Mi opinión honesta: para una sola familia, **la mayoría de funcionalidades van mejor en PostgreSQL**. Tareas, descripciones, estadísticas, logros y avatares van a PostgreSQL, sin discusión (además, datos de menores ⇒ RGPD, y en una cadena pública no se pueden borrar).

Donde sí hay un argumento defendible, y encaja con la educación financiera:

- **Compromisos que ni el padre ni el servidor pueden alterar a posteriori.** Si el padre promete 10 por una tarea y lo registra, el hijo puede comprobar que el pago existe y que nadie lo ha reescrito. Con una base de datos, el administrador (o el padre con acceso) puede modificarlo. Es el concepto de *registro verificable* explicado con algo que un niño entiende.
- **Reglas de ahorro ejecutadas por código.** Por ejemplo, un objetivo de ahorro "bloqueado hasta una fecha" que ni el padre puede liberar antes: enseña compromiso y coste de oportunidad, y la garantía la da el contrato, no la buena voluntad. Esta es probablemente la funcionalidad con más fuerza académica.
- **Propiedad real del saldo:** el hijo firma sus propios gastos y el padre no puede gastar por él (ya está en `spend`).

El contraargumento que te pondrá el tribunal: *"en una familia hay confianza; un log de auditoría inmutable en PostgreSQL da casi lo mismo"*. Por eso te recomiendo que el TFG **incluya la comparación como parte de la evaluación**: implementar el núcleo (recompensa + gasto + objetivo bloqueado) con blockchain y medirlo frente a la versión tradicional en seguridad, latencia, coste, complejidad y UX. Así la tesis no es "blockchain es mejor", sino "cuándo compensa y cuándo no", que es mucho más defendible.

**Representación del dinero (tu punto 11), recomendación para la PoC y el MVP:**

| Opción | Veredicto |
|---|---|
| Moneda nativa (ETH) | No: volatilidad, regulación y dinero real en manos de menores |
| Token ERC-20 | No por ahora: añade transferibilidad fuera de la familia, que no aporta nada educativo y complica la regulación |
| **Saldo interno del contrato** | **Sí (lo que implementa `FamilyLedger`).** Reglas propias, sin valor económico, sin transferencias fuera de la familia |
| Puntos sin valor en PostgreSQL | Es la alternativa tradicional, y sirve de línea base para la comparación |

### 12.4 El problema de los niños con wallet (tu punto 14): propuesta mínima

Para el MVP **no** metería *account abstraction*, *smart accounts* ni *embedded wallets*: cada una es un proyecto en sí misma. Lo razonable es:
- **El padre** usa MetaMask (es el adulto y el que firma los compromisos).
- **El hijo** tiene una cuenta MetaMask creada y custodiada por el padre en el dispositivo familiar (se simula así en la PoC con la cuenta `Hijo`), o, en su versión más simple, el hijo no firma on-chain y sus gastos los registra el padre.
- Smart Accounts Kit de MetaMask (delegaciones y ERC-7715 *Advanced Permissions*) quedaría como **trabajo futuro** citado en la memoria: permitiría que el padre delegue al hijo permisos acotados (p. ej. "gastar hasta X por semana"), que es la solución elegante pero no necesaria para demostrar la tesis.

### 12.5 Qué llevar a la reunión con el tutor
1. Checklist de la sección 11 completada, con hashes de Sepolia.
2. Un vídeo corto del flujo móvil (M1/M2).
3. La tabla 12.2 y el párrafo 12.3 con tu propuesta de funcionalidades on-chain.
4. Tu recomendación: sí o no, con el criterio que no se cumpla si es no.

---

## Calendario propuesto hasta el 9 de octubre

| Día | Tarea |
|---|---|
| Mié 1 oct | Instalar herramientas, wallet, faucet. Ejecutar los tests en local y entender el contrato |
| Jue 2 oct | Nivel 1 y 2 con MetaMask real, backend incluido |
| Vie 3 oct | Nivel 3 en Sepolia: despliegue, transferencia, contrato, rechazos |
| Sáb 4 – dom 5 oct | Prueba móvil M1/M2 y recoger evidencias y medidas |
| Lun 6 oct | Conclusiones (sección 12) y reunión o correo al tutor |
| Mar 7 – jue 9 oct | Plan de Trabajo (objetivos, tareas, Gantt) según la decisión |

Si el nivel 3 en Sepolia no está funcionando el viernes 3, eso ya es un dato para la decisión.

---

## Fuentes consultadas (30-09-2026)

- MetaMask Connect EVM (sustituto de `@metamask/sdk`): https://docs.metamask.io/metamask-connect/evm/
- Métodos del cliente EVM (API key de Infura obligatoria): https://docs.metamask.io/metamask-connect/evm/reference/methods/
- Plataformas soportadas por MetaMask Connect: https://docs.metamask.io/metamask-connect/supported-platforms/
- Firma de datos (personal_sign, signTypedData_v4, eth_sign obsoleto): https://docs.metamask.io/metamask-connect/evm/guides/sign-data/
- SDK Android archivado (26-02-2026): https://github.com/MetaMask/metamask-android-sdk
- Embedded Wallets Android: https://docs.metamask.io/embedded-wallets/sdk/android/
- Smart Accounts Kit: https://docs.metamask.io/smart-accounts-kit/
- Dominios de deeplink (`link.metamask.io`, `metamask.app.link`): AndroidManifest de https://github.com/MetaMask/metamask-mobile
- Redes de Ethereum (Sepolia recomendada, Holesky obsoleta): https://ethereum.org/en/developers/docs/networks/
- Propuesta de sustitución de Sepolia: https://ethereum-magicians.org/t/sepolia-testnet-replacement-sunsetting/28647
- Hardhat 3 (Node ≥ 22.13, plantilla viem, Ignition, verificación): https://hardhat.org/docs/getting-started y https://hardhat.org/docs/tutorial/verifying
- Web3j (LF Decentralized Trust, Java 21): https://github.com/LFDT-web3j/web3j y Maven Central `org.web3j:core` 6.0.0
- Versiones de npm comprobadas con `npm view`: hardhat 3.18.0, viem 2.57.1, vite 8.3.1, @metamask/connect-evm 2.1.1; Spring Boot 4.1.1 en Maven Central
