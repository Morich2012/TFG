# Evidencias de la PoC en Sepolia

Ejecución real del 1 de octubre de 2026 con MetaMask (extensión de navegador), la dApp de `poc/dapp` y el backend de `poc/backend`.

## Cuentas (solo testnet)

| Rol | Dirección |
|---|---|
| Padre | [`0x8481cDD829cfdc8d56F7023171C579B304fD4569`](https://sepolia.etherscan.io/address/0x8481cDD829cfdc8d56F7023171C579B304fD4569) |
| Hijo | [`0xbbF25221a405A2570d4b3e0FA32375aeA256282C`](https://sepolia.etherscan.io/address/0xbbF25221a405A2570d4b3e0FA32375aeA256282C) |
| Extraño | [`0x111B30913F6C96505a36c1dc51Eabd168101dAaD`](https://sepolia.etherscan.io/address/0x111B30913F6C96505a36c1dc51Eabd168101dAaD) |
| Contrato `FamilyLedger` | [`0x452e2e40a0fb9b5b299e044fb271b2e90ce15338`](https://sepolia.etherscan.io/address/0x452e2e40a0fb9b5b299e044fb271b2e90ce15338) |

(El primer despliegue, `0xd7Fa5F0F7C54D5552cfe6d01b5C385162889CCb4`, se usó durante la depuración del cambio de cuenta.)

## Checklist

| # | Prueba | Resultado | Evidencia |
|---|---|---|---|
| N1.1 | Detecta MetaMask vía EIP-6963 | ✅ | registro de la dApp |
| N1.2 | Conecta y muestra la cuenta | ✅ | `Conectar: OK` |
| N1.3 | Cambio de cuenta reflejado sin recargar | ✅ (tras conectar las 3 cuentas al sitio) | `accountsChanged → 0x111B…`, `→ 0xbbF2…` |
| N2.1 | Firma verificada en el navegador | ✅ con las 3 cuentas | `válida=true` |
| N2.2 | Login con Spring Boot | ✅ con las 3 cuentas | `HTTP 200 {"valid":true,…}` |
| N2.3 | Nonce de un solo uso (anti-replay) | ✅ (test automático) | `AuthControllerTest` |
| N3.1 | Transferencia de 0.0001 SepoliaETH Padre → Hijo | ✅ bloque 11823688, 21.000 gas, 12,7 s | [0x69edc34f…](https://sepolia.etherscan.io/tx/0x69edc34fa6ca2a66a55cdb35bbe0ae4af5899fbc6f80e4117341d25c54f21af6) |
| N3.2 | Despliegue desde MetaMask | ✅ bloque 11823692, 373.968 gas, 25,2 s | [0xbc6a91d2…](https://sepolia.etherscan.io/tx/0xbc6a91d24f9cb051a88872f4e051d5872c1d59faaddcd1535157d6b998133690) |
| N3.3a | `addChild(Hijo)` por el Padre | ✅ bloque 11823694, 135.124 gas, 13,1 s | [0xbd2823ce…](https://sepolia.etherscan.io/tx/0xbd2823cefa17bfca60b8a9702f4f748062d41f1abebf233e51bedcb42af88dd6) |
| N3.3b | `reward(Hijo, task, 10)` por el Padre | ✅ bloque 11823696, 161.057 gas, 14,7 s | [0x3e65d4b0…](https://sepolia.etherscan.io/tx/0x3e65d4b01e0568a30bf07c1491d9506059de7c6b8516b349e25dd624369097f0) |
| N3.4 | Duplicado rechazado | ✅ `TaskAlreadyRewarded`, detectado en simulación (1,8 s, sin gas) | registro |
| N3.5 | Extraño rechazado | ✅ `NotParent`, detectado en simulación (1,8 s, sin gas) | registro |
| N3.3c | `spend(3)` firmado por el Hijo | ✅ bloque 11823707, 133.534 gas, 22,2 s | [0xb745b6c2…](https://sepolia.etherscan.io/tx/0xb745b6c2eb3adc76a4dda843d9fa5343ea241e84e7b45383320b23ca03612735) |
| N3.6 | Estado final | ✅ `parent = Padre`, `isChild(Hijo) = true`, `balance = 7` | `Leer contrato: OK en 369 ms` |
| M1 | dApp en el navegador de MetaMask Mobile | ⏳ pendiente | |
| M2 | App Android (Java) → deeplink → MetaMask | ⏳ pendiente | |

## Incidencias encontradas

1. **Cambio de cuenta silencioso.** Con solo el Padre conectado al sitio, cambiar a Hijo o Extraño en MetaMask no avisaba a la dApp, que seguía firmando como Padre. Se corrigió con el botón *Elegir cuentas* y mostrando el rol de quien firma (commit `d72234f`).
2. **Las cuentas son smart accounts EIP-7702.** Las llamadas al contrato pasan por el `DelegationManager` de MetaMask (`redeemDelegations`), con unos 90.000-100.000 de gas extra por operación respecto a una cuenta normal. Detalle en [explicacion-poc.md](explicacion-poc.md#52-gas-local-vs-sepolia-real).

## PoC 2: gasto con la huella desde Android (6 de octubre de 2026)

App `poc/android-child` en el emulador de Android Studio, backend `poc/backend` como relayer y contrato `DeviceKeyLedger` desplegado por el relayer.

| Rol | Dirección |
|---|---|
| Relayer (paga el gas; en la PoC también hace de padre) | [`0x302D832f091f8f50afc18574ce308288374bbfe3`](https://sepolia.etherscan.io/address/0x302D832f091f8f50afc18574ce308288374bbfe3) |
| Contrato `DeviceKeyLedger` | [`0xee11157fc4717b776c7bd0c15587e1877ca4178d`](https://sepolia.etherscan.io/address/0xee11157fc4717b776c7bd0c15587e1877ca4178d) |

| # | Prueba | Resultado | Evidencia |
|---|---|---|---|
| H1 | Despliegue del contrato por el relayer | ✅ 582.059 gas | [0x03d61b49…](https://sepolia.etherscan.io/tx/0x03d61b49eae9b90ecb2ffdfec663dd25ae191c2ea6d5fc8c2c5930ed51edf6e3) |
| H2 | Clave P-256 en el Android Keystore con huella obligatoria | ✅ creada en 1.083 ms (en *software*: el emulador no tiene chip seguro) | registro de la app |
| H3 | Registrar el móvil (`registerDevice`) | ✅ bloque 11855627, 69.171 gas, 12,0 s | [0x4bce6f40…](https://sepolia.etherscan.io/tx/0x4bce6f40268001d82b0ad06d1fc4a6ee2d7cfa2581a7b4df03885e5490a32fc2) |
| H4 | Recompensa +10 (`reward`) | ✅ bloque 11855630, 73.520 gas, 18,5 s | [0x9893ff23…](https://sepolia.etherscan.io/tx/0x9893ff2322d18ce86114b749a11a5c09e8272b94a8f1dfe0cebaa9972f878083) |
| H5 | **Gastar 3 en cromos poniendo el dedo** (`spendSigned`) | ✅ "¡Hecho!" en **400 ms**; confirmado en bloque 11855632 a los **6,6 s**; **66.395 gas** | [0x1f67f35c…](https://sepolia.etherscan.io/tx/0x1f67f35c73eb833ea4e3a9049323b06521204eba2ac1e1a3ca3d85630326d26d) |
| H6 | Recompensa antes de registrar el móvil | ✅ rechazada con `UnknownChild` sin gastar gas | registro de la app |
| H7 | Huella real en un móvil con TEE/StrongBox | ⏳ pendiente | |

Comparado con el mismo gasto con MetaMask (N3.3c): **66.395 frente a 133.534 de gas**, el niño no ve ninguna ventana técnica ni necesita ETH, y la app responde en 0,4 s en lugar de 22 s.
