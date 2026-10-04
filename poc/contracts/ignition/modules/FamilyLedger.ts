import { buildModule } from "@nomicfoundation/hardhat-ignition/modules";

export default buildModule("FamilyLedgerModule", (m) => {
  const ledger = m.contract("FamilyLedger");
  return { ledger };
});
