# Alternativas a MetaMask para que un niño use la app sin complicaciones

*3 de octubre de 2026 · Continuación de [explicacion-poc.md](explicacion-poc.md). Todo lo que se afirma aquí está comprobado con la documentación actual o con tests en este repositorio (indicado en cada caso).*

## El problema que queremos resolver

Con MetaMask, en la PoC del 1 de octubre, cada acción del niño suponía:
1. Una ventana con direcciones y gas que hay que entender y confirmar.
2. Una wallet propia con su frase de recuperación de 12 palabras.
3. Saldo de ETH para pagar el gas.
4. Esperar entre 13 y 25 segundos.

Para un niño queremos **una confirmación natural (como mucho poner el dedo), ninguna wallet visible, sin gas y sin esperas**, y que se pueda hacer en Android con Java.

## Opciones comparadas

| Opción | Qué hace el niño | ¿Wallet o frase? | ¿Android en Java? | Dependencias externas | Complejidad |
|---|---|---|---|---|---|
| **A. MetaMask** (lo probado) | Confirmar una ventana técnica en cada acción | Sí | No (SDK archivado) | MetaMask | Media |
| **B. Wallet embebida** (MetaMask Embedded Wallets, antes Web3Auth) | Entrar con Google una vez; la app firma sola | No | **Sí**, SDK Android v10.0.1 + Web3j | Proveedor externo, *client ID* y su red de claves | Media |
| **C. Clave del móvil con huella** (Android Keystore P-256 + backend "relayer") | **Poner el dedo** | No | **Sí**, todo Java (JCA + Web3j) | Ninguna | Media |
| **D. Passkeys** (WebAuthn, sincronizadas con Google) | Poner el dedo; se recupera en otro móvil | No | Sí (Credential Manager), pero verificar WebAuthn on-chain es más delicado | Gestor de contraseñas de Google | Media-alta |
| **E. Smart accounts ERC-4337 + paymaster + *session keys*** | Nada, o poner el dedo | No | Las librerías son TypeScript | *Bundler* y *paymaster* (Pimlico, Alchemy, Coinbase…) | Alta |
| **F. Blockchain privada** (Hyperledger Besu) | Lo que se elija arriba | — | Sí | Ninguna | Alta, y pierde la justificación (un único administrador) |
| **G. Sin blockchain** (PostgreSQL, opcionalmente con cadena de hashes) | Nada | No | Sí | Ninguna | Baja |

Notas sobre las opciones:
- **B** es la más rápida de integrar: tras el login, el SDK devuelve la clave privada a la app (`web3Auth.getPrivateKey()`) y se firma con Web3j sin ventanas ([docs](https://docs.metamask.io/embedded-wallets/sdk/android/)). Pero el niño necesita una cuenta de Google (con menos de 14 años, gestionada por los padres con Family Link), la clave pasa por la memoria de la app y dependes de un proveedor y de su plan de precios (no está publicado en la documentación).
- **E** es la solución "de la industria", pero en Android con Java no hay librerías maduras: obliga a TypeScript o React Native y a depender de un *bundler* de terceros. Es demasiado para un TFG con plazo.
- **G** sigue siendo perfectamente válida si el tutor prefiere la vía tradicional.

## Recomendación: opción C, la clave del móvil con huella

```
NIÑO (app Android en Java)                     BACKEND (Spring Boot)              BLOCKCHAIN
┌──────────────────────────────┐   firma +    ┌────────────────────────┐  tx   ┌──────────────────────┐
│ "¿Gastar 4 en cromos?"       │──operación──▶│ guarda en PostgreSQL   │──────▶│ DeviceKeyLedger      │
│ [ poner el dedo ]            │              │ envía la tx y PAGA EL  │       │ verifica la firma    │
│ clave P-256 en el chip seguro│◀─"hecho" ────│ GAS con su propia      │       │ P-256 del móvil      │
│ (Android Keystore)           │   al instante│ cuenta (relayer)       │       │ (precompilado 0x100) │
└──────────────────────────────┘              └────────────────────────┘       └──────────────────────┘
PADRE: registra el móvil del hijo y da recompensas (con MetaMask, o también con su propia clave del móvil)
```

Cómo lo vive el niño: pulsa "Gastar", pone el dedo y ve "¡Hecho!" al momento. La confirmación en la blockchain llega en segundo plano unos segundos después y la app la marca con un ✓. No ve direcciones, gas ni frases.

Por qué es la mejor para tu TFG:
1. **Es todo Java.** En Android: `KeyPairGenerator` con `"AndroidKeyStore"`, curva `secp256r1` y `setUserAuthenticationRequired(true)` para pedir la huella; se firma con `Signature.getInstance("SHA256withECDSA")`. En Spring Boot, el relayer usa Web3j.
2. **La clave nunca sale del chip seguro del móvil**, ni siquiera la app puede leerla. Es más seguro que MetaMask y que la opción B.
3. **El backend paga el gas pero no puede hacer trampas.** El contrato comprueba la firma del móvil del hijo: el servidor no puede inventar un gasto, cambiar la cantidad ni repetir uno. **Esto refuerza el argumento académico**: ni los padres ni quien opera la app pueden falsear las operaciones del niño. Con una base de datos normal, el administrador sí podría.
4. **La recuperación es natural en una familia**: si el niño pierde el móvil, el padre registra el nuevo y el saldo sigue ahí, porque va asociado al hijo y no al móvil.
5. **Es tecnología actual y poco explotada.** Android Keystore no soporta la curva de Ethereum (secp256k1), solo P-256. Desde la actualización Fusaka (3 de diciembre de 2025, también en Sepolia), Ethereum verifica firmas P-256 de forma nativa con el precompilado [EIP-7951](https://eips.ethereum.org/EIPS/eip-7951) en la dirección `0x100`, a 6.900 de gas. Es un punto muy defendible en la memoria.

## Lo que ya está validado (hoy, en este repositorio)

| Pieza | Dónde | Resultado |
|---|---|---|
| Contrato `DeviceKeyLedger` | `poc/contracts/contracts/DeviceKeyLedger.sol` | Usa `P256.verifyNative` de OpenZeppelin 5.6.1 |
| 9 tests del contrato | `poc/contracts/test/DeviceKeyLedger.ts` | **9/9 en verde**: gasto válido enviado por el relayer, el relayer no puede cambiar la cantidad, la firma no se reutiliza, rechazo de otro móvil, rechazo de firmas con *s* alta, saldo insuficiente y solo el padre registra móviles |
| Firmador en Java (lo que hará Android) | `poc/device-key/` | 2 tests en verde. Convierte la firma DER de Java/Android a (r, s) y la normaliza a *s* baja, como exige el contrato |
| **Interoperabilidad Java → Solidity** | test "acepta una firma generada en Java" | **En verde**: una firma hecha con `SHA256withECDSA` en Java es aceptada por el contrato |

**Gas medido** (red simulada): un gasto firmado con la clave del móvil cuesta **~68.650**, frente a los **133.534** del mismo gasto con MetaMask en Sepolia (por la smart account EIP-7702). Registrar el móvil cuesta ~69.900 (una vez) y el despliegue ~927.000.

**Actualización (4 de octubre):** ya están hechos la app Android ([`poc/android-child`](../poc/android-child/README.md)) y el relayer en Spring Boot. Probados juntos en una red local: registrar el móvil 69.195 de gas, recompensar 73.520 y gastar 66.395, con respuesta a la app en 39 ms. Falta la prueba con la huella real en un móvil y en Sepolia.

## Límites que hay que reconocer en la memoria

- **El backend tiene una clave**, la del relayer, pero solo sirve para pagar el gas: no controla el dinero de nadie ni puede falsificar operaciones. Sí podría *retrasar* o *no enviar* una operación (censura); se mitiga porque el padre puede enviarla él mismo.
- **La latencia de la red sigue ahí** (unos 12 s en Sepolia). Se oculta con una interfaz optimista ("hecho", y el ✓ llega después), o se reduce desplegando en una red L2 de pruebas que también tenga el precompilado P-256 (RIP-7212 en la misma dirección).
- **Un móvil sin chip seguro** guardaría la clave por software. Android permite comprobarlo con *key attestation*, lo que podría ser un punto extra.
- **Sigue siendo más complejo que una base de datos.** Esta opción solo compensa si el tutor valora el argumento "nadie, ni el administrador, puede falsear las operaciones".

## Propuesta de siguiente paso

1. Contarle al tutor esta tercera vía junto a las dos del correo anterior.
2. Si le encaja, PoC 2 antes del 9 de octubre: una app Android mínima en Java (botón, huella y firma) más un endpoint de relayer en Spring Boot que lo envíe a Sepolia. El contrato y el firmador ya están hechos y probados.
