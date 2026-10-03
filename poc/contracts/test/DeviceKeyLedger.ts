import assert from "node:assert/strict";
import { generateKeyPairSync, sign, type KeyObject } from "node:crypto";
import { readFileSync } from "node:fs";
import { describe, it } from "node:test";

import { network } from "hardhat";
import { type Hex, encodeAbiParameters, keccak256, sha256, toHex } from "viem";

// Orden de la curva P-256: la app debe enviar la firma con s <= N/2 (s "baja").
const N = 0xffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551n;

const CHILD = keccak256(toHex("child:1")); // id opaco del hijo: nada personal on-chain
const TASK = keccak256(toHex("task:1"));
const CONCEPT = keccak256(toHex("concepto:cromos"));

/** Simula el móvil del hijo: clave P-256 (la misma curva que Android Keystore). */
function newDevice() {
  const { privateKey, publicKey } = generateKeyPairSync("ec", { namedCurve: "P-256" });
  const jwk = publicKey.export({ format: "jwk" });
  const toHex32 = (b64url: string) => `0x${Buffer.from(b64url, "base64url").toString("hex")}` as Hex;
  return { privateKey, x: toHex32(jwk.x!), y: toHex32(jwk.y!) };
}

function signP256(privateKey: KeyObject, message: Hex, lowS = true): { r: Hex; s: Hex } {
  const raw = sign("sha256", Buffer.from(message.slice(2), "hex"), { key: privateKey, dsaEncoding: "ieee-p1363" });
  const r = BigInt(`0x${raw.subarray(0, 32).toString("hex")}`);
  let s = BigInt(`0x${raw.subarray(32).toString("hex")}`);
  if (lowS && s > N / 2n) s = N - s;
  if (!lowS && s <= N / 2n) s = N - s;
  return { r: toHex(r, { size: 32 }), s: toHex(s, { size: 32 }) };
}

describe("DeviceKeyLedger (firma con la clave del móvil, sin wallet)", async function () {
  const { viem } = await network.create();
  const publicClient = await viem.getPublicClient();
  const [parent, relayer, stranger] = await viem.getWalletClients();
  const chainId = BigInt(await publicClient.getChainId());

  async function setup(device = newDevice()) {
    const ledger = await viem.deployContract("DeviceKeyLedger");
    await ledger.write.registerDevice([CHILD, device.x, device.y]);
    await ledger.write.reward([CHILD, TASK, 10n]);
    return { ledger, device };
  }

  /** Lo que firma el móvil: abi.encode(chainid, contrato, hijo, concepto, cantidad, nonce). */
  function spendMessage(contract: Hex, amount: bigint, nonce: bigint) {
    return encodeAbiParameters(
      [{ type: "uint256" }, { type: "address" }, { type: "bytes32" }, { type: "bytes32" }, { type: "uint256" }, { type: "uint256" }],
      [chainId, contract, CHILD, CONCEPT, amount, nonce],
    );
  }

  it("el digest del contrato coincide con lo que firma el móvil", async function () {
    const { ledger } = await setup();
    const message = spendMessage(ledger.address, 4n, 0n);
    assert.equal(await ledger.read.spendDigest([CHILD, CONCEPT, 4n, 0n]), sha256(message));
  });

  it("el backend (relayer) envía un gasto firmado por el móvil del hijo y se ejecuta", async function () {
    const { ledger, device } = await setup();
    const { r, s } = signP256(device.privateKey, spendMessage(ledger.address, 4n, 0n));

    await viem.assertions.emitWithArgs(
      ledger.write.spendSigned([CHILD, CONCEPT, 4n, r, s], { account: relayer.account }),
      ledger,
      "Spent",
      [CHILD, CONCEPT, 4n, 0n],
    );
    assert.equal(await ledger.read.balanceOf([CHILD]), 6n);
    assert.equal(await ledger.read.nonceOf([CHILD]), 1n);
  });

  it("el relayer no puede cambiar la cantidad que firmó el hijo", async function () {
    const { ledger, device } = await setup();
    const { r, s } = signP256(device.privateKey, spendMessage(ledger.address, 4n, 0n));
    await viem.assertions.revertWithCustomError(
      ledger.write.spendSigned([CHILD, CONCEPT, 9n, r, s], { account: relayer.account }),
      ledger,
      "InvalidSignature",
    );
  });

  it("la misma firma no se puede usar dos veces (nonce)", async function () {
    const { ledger, device } = await setup();
    const { r, s } = signP256(device.privateKey, spendMessage(ledger.address, 4n, 0n));
    await ledger.write.spendSigned([CHILD, CONCEPT, 4n, r, s], { account: relayer.account });
    await viem.assertions.revertWithCustomError(
      ledger.write.spendSigned([CHILD, CONCEPT, 4n, r, s], { account: relayer.account }),
      ledger,
      "InvalidSignature",
    );
  });

  it("rechaza la firma de otro móvil", async function () {
    const { ledger } = await setup();
    const other = newDevice();
    const { r, s } = signP256(other.privateKey, spendMessage(ledger.address, 4n, 0n));
    await viem.assertions.revertWithCustomError(
      ledger.write.spendSigned([CHILD, CONCEPT, 4n, r, s], { account: stranger.account }),
      ledger,
      "InvalidSignature",
    );
  });

  it("rechaza firmas con s alta: la app debe normalizarlas", async function () {
    const { ledger, device } = await setup();
    const { r, s } = signP256(device.privateKey, spendMessage(ledger.address, 4n, 0n), false);
    await viem.assertions.revertWithCustomError(
      ledger.write.spendSigned([CHILD, CONCEPT, 4n, r, s]),
      ledger,
      "InvalidSignature",
    );
  });

  it("no deja gastar más de lo que hay aunque la firma sea válida", async function () {
    const { ledger, device } = await setup();
    const { r, s } = signP256(device.privateKey, spendMessage(ledger.address, 11n, 0n));
    await viem.assertions.revertWithCustomErrorWithArgs(
      ledger.write.spendSigned([CHILD, CONCEPT, 11n, r, s]),
      ledger,
      "InsufficientBalance",
      [10n, 11n],
    );
  });

  it("solo el padre registra dispositivos y la clave debe ser válida", async function () {
    const ledger = await viem.deployContract("DeviceKeyLedger");
    const device = newDevice();
    await viem.assertions.revertWithCustomError(
      ledger.write.registerDevice([CHILD, device.x, device.y], { account: stranger.account }),
      ledger,
      "NotParent",
    );
    await viem.assertions.revertWithCustomError(
      ledger.write.registerDevice([CHILD, device.x, toHex(1n, { size: 32 })]),
      ledger,
      "InvalidPublicKey",
    );
  });

  it("acepta una firma generada en Java (SHA256withECDSA, como en Android)", async function () {
    const fixture = JSON.parse(
      readFileSync(new URL("./fixtures/java-device-signature.json", import.meta.url), "utf8"),
    );
    // La firma de Java está hecha para el primer contrato desplegado en una red recién creada.
    const fresh = await network.create();
    const ledger = await fresh.viem.deployContract("DeviceKeyLedger");
    assert.equal(ledger.address.toLowerCase(), fixture.contract.toLowerCase());

    await ledger.write.registerDevice([fixture.childId, fixture.x, fixture.y]);
    await ledger.write.reward([fixture.childId, TASK, 10n]);
    await ledger.write.spendSigned([fixture.childId, fixture.conceptId, BigInt(fixture.amount), fixture.r, fixture.s]);
    assert.equal(await ledger.read.balanceOf([fixture.childId]), 6n);
  });
});
