import assert from "node:assert/strict";
import { describe, it } from "node:test";

import { network } from "hardhat";
import { keccak256, toHex } from "viem";

// Los identificadores reales viven en PostgreSQL; on-chain solo va su hash.
const TASK_1 = keccak256(toHex("task:1"));
const CONCEPT = keccak256(toHex("concept:cine"));

describe("FamilyLedger", async function () {
  const { viem } = await network.create();
  const [parent, child, stranger] = await viem.getWalletClients();

  async function deployWithChild() {
    const ledger = await viem.deployContract("FamilyLedger");
    await ledger.write.addChild([child.account.address]);
    return ledger;
  }

  it("el despliegue fija al padre", async function () {
    const ledger = await viem.deployContract("FamilyLedger");
    assert.equal(
      (await ledger.read.parent()).toLowerCase(),
      parent.account.address.toLowerCase(),
    );
  });

  it("el padre recompensa una tarea y emite RewardPaid", async function () {
    const ledger = await deployWithChild();
    await viem.assertions.emitWithArgs(
      ledger.write.reward([child.account.address, TASK_1, 10n]),
      ledger,
      "RewardPaid",
      [child.account.address, TASK_1, 10n],
    );
    assert.equal(await ledger.read.balanceOf([child.account.address]), 10n);
  });

  it("rechaza recompensas de alguien que no es el padre", async function () {
    const ledger = await deployWithChild();
    await viem.assertions.revertWithCustomError(
      ledger.write.reward([child.account.address, TASK_1, 10n], {
        account: stranger.account,
      }),
      ledger,
      "NotParent",
    );
  });

  it("rechaza pagar dos veces la misma tarea", async function () {
    const ledger = await deployWithChild();
    await ledger.write.reward([child.account.address, TASK_1, 10n]);
    await viem.assertions.revertWithCustomErrorWithArgs(
      ledger.write.reward([child.account.address, TASK_1, 10n]),
      ledger,
      "TaskAlreadyRewarded",
      [TASK_1],
    );
  });

  it("rechaza recompensar a una dirección que no es hijo", async function () {
    const ledger = await deployWithChild();
    await viem.assertions.revertWithCustomError(
      ledger.write.reward([stranger.account.address, TASK_1, 10n]),
      ledger,
      "NotChild",
    );
  });

  it("el hijo gasta desde su cuenta y no puede gastar más de lo que tiene", async function () {
    const ledger = await deployWithChild();
    await ledger.write.reward([child.account.address, TASK_1, 10n]);

    await viem.assertions.emitWithArgs(
      ledger.write.spend([CONCEPT, 4n], { account: child.account }),
      ledger,
      "Spent",
      [child.account.address, CONCEPT, 4n],
    );
    assert.equal(await ledger.read.balanceOf([child.account.address]), 6n);

    await viem.assertions.revertWithCustomErrorWithArgs(
      ledger.write.spend([CONCEPT, 7n], { account: child.account }),
      ledger,
      "InsufficientBalance",
      [6n, 7n],
    );
  });

  it("el padre no puede gastar por el hijo", async function () {
    const ledger = await deployWithChild();
    await viem.assertions.revertWithCustomError(
      ledger.write.spend([CONCEPT, 1n]),
      ledger,
      "NotChild",
    );
  });
});
