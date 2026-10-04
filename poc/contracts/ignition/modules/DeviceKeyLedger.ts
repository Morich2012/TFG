import { buildModule } from "@nomicfoundation/hardhat-ignition/modules";

// Alternativa al despliegue desde el backend (POST /ledger/deploy).
// Ojo: quien despliega es el "padre" del contrato, y el relayer solo puede registrar móviles
// y dar recompensas si es él quien despliega. Por eso en la PoC lo despliega el propio backend.
export default buildModule("DeviceKeyLedgerModule", (m) => {
  const ledger = m.contract("DeviceKeyLedger");
  return { ledger };
});
