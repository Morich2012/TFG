# PoC 2: la hucha del hijo con huella (Android, Java)

El niño gasta poniendo el dedo. No hay wallet, ni frase de recuperación, ni gas, ni MetaMask.

```
App Android (este proyecto)          Backend (poc/backend)              Sepolia
clave P-256 en el Keystore  ──firma──▶ relayer: simula y envía, ──tx──▶ DeviceKeyLedger
"¿Gastar 3 en cromos?" + dedo          paga el gas con su clave         verifica la firma del
                                       (no puede falsificar gastos)     móvil (EIP-7951, 0x100)
```

| Fichero | Qué hace |
|---|---|
| `DeviceKey.java` | Crea la clave en el Android Keystore (StrongBox si existe) con huella obligatoria en cada firma |
| `SpendMessage.java` | Mensaje que se firma, igual que `abi.encode` del contrato, y paso de firma DER a (r, s) con s baja |
| `RelayerApi.java` | Llamadas HTTP al backend (`/ledger/...`) |
| `MainActivity.java` | Pantalla: saldo, *Gastar con la huella*, configuración y registro con tiempos |
| `SpendMessageTest.java` | Comprueba que la app codifica igual que el contrato (con la firma Java que el contrato ya acepta) |

Sin librerías externas: todo es API de Android (minSdk 30, Android 11 o superior).

## Cómo probarlo

### 1. Backend con el relayer

```bash
cd poc/backend
mvn spring-boot:run
```

En el arranque aparece `Relayer 0x…`: es una cuenta nueva que el backend crea y guarda en
`~/.tfg-poc/relayer.key` (fuera del repositorio). Su clave solo sirve para pagar el gas.

1. Desde MetaMask, con la cuenta **Padre** y la red Sepolia, envía **0.02 SepoliaETH** a esa dirección.
2. Despliega el contrato (lo hace el relayer, que queda como "padre" del contrato en esta PoC):
   ```bash
   curl -X POST http://localhost:8080/ledger/deploy        # en PowerShell: curl.exe -X POST ...
   curl http://localhost:8080/ledger                        # contrato, relayer y su saldo
   ```
   La dirección del contrato se guarda en `~/.tfg-poc/` y se reutiliza en los siguientes arranques.

### 2. App en Android Studio

1. *File → Open* y elige la carpeta `poc/android-child`. Android Studio descarga Gradle 9.6 y el plugin de Android.
2. **Emulador:** crea un dispositivo con Android 15 o 16. En *Ajustes → Seguridad*, pon un PIN y añade una huella:
   cuando la pida, pulsa *Extended controls (⋯) → Fingerprint → Touch sensor*. La URL del backend por defecto
   (`http://10.0.2.2:8080`) ya apunta a tu PC.
3. **Móvil real** (mejor, porque usa el chip seguro de verdad): activa la depuración USB, conecta el móvil y,
   en la app, cambia *Backend* por `http://IP-DE-TU-PC:8080` (mismo wifi; si no conecta, permite el puerto 8080 en el
   firewall).
4. Pulsa *Run*.

### 3. Prueba en la app

1. **Crear la clave del móvil.** El registro muestra dónde quedó la clave (StrongBox, TEE o software) y su (x, y).
2. **Registrar este móvil.** El backend llama a `registerDevice`; tarda lo que un bloque (unos 12 s).
3. **Recompensa de prueba +10.** El saldo pasa a 10.
4. **Gastar con la huella** (por ejemplo, 3 en cromos). Aparece *¿Gastar 3 en cromos?*, pones el dedo y:
   - *¡Hecho!* en menos de un segundo (el backend ya ha comprobado la firma simulando la llamada);
   - *✓ Confirmado en la blockchain* unos segundos después, con el enlace a Etherscan. El saldo pasa a 7.
5. Prueba también a cancelar la huella y a gastar más de lo que hay (*No tienes saldo suficiente*, sin gastar gas).

Copia el registro de la app como evidencia, igual que en la PoC de MetaMask.

## Qué se ha comprobado sin un móvil

- El código compila contra la API de Android 16 (API 36).
- `SpendMessageTest`: la codificación de la app coincide con la del contrato (2 tests).
- La app y el backend juntos en una red local de Hardhat, con una clave software en lugar del Keystore:
  registrar 69.195 de gas, recompensar 73.520, gastar 66.395; *¡Hecho!* en 39 ms; la misma firma repetida y la
  cantidad cambiada se rechazan con `InvalidSignature`.

Lo que solo puedes comprobar tú: la huella real, el Keystore del móvil y Sepolia.

## Simplificaciones de la PoC

- El backend hace de padre (registra el móvil y da recompensas). En el TFG lo haría la app del padre,
  firmando con su propia clave del móvil, igual que el hijo.
- La app se fía del backend para saber la red y el contrato. El backend no puede usar mal la firma (solo vale
  para esa red, ese contrato, ese gasto y ese nonce), pero en el TFG la dirección del contrato iría fija en la app.
