// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

import {P256} from "@openzeppelin/contracts/utils/cryptography/P256.sol";

/// @title DeviceKeyLedger (PoC 2)
/// @notice Variante sin MetaMask para el hijo: firma con una clave P-256 que vive en el chip seguro
///         de su móvil (Android Keystore, desbloqueada con la huella). El backend actúa de "relayer":
///         envía la transacción y paga el gas, pero NO puede inventar ni alterar operaciones,
///         porque el contrato comprueba la firma del dispositivo del hijo.
/// @dev El hijo no tiene cuenta Ethereum: se identifica con un bytes32 opaco (hash de su id en PostgreSQL).
///      La verificación P-256 usa solo el precompilado EIP-7951 (0x100, activo en Ethereum y Sepolia desde
///      Fusaka) vía OpenZeppelin `verifyNative`: en una red sin él, revierte en lugar de usar la versión
///      en Solidity (mucho más cara y que exige compilar con viaIR).
contract DeviceKeyLedger {
    struct DeviceKey {
        bytes32 x;
        bytes32 y;
    }

    address public immutable parent;

    mapping(bytes32 childId => DeviceKey) public deviceKeyOf;
    mapping(bytes32 childId => uint256) public balanceOf;
    /// @notice Contador por hijo: cada firma solo vale una vez (anti-replay).
    mapping(bytes32 childId => uint256) public nonceOf;
    mapping(bytes32 taskId => bool) public taskRewarded;

    event DeviceRegistered(bytes32 indexed childId, bytes32 x, bytes32 y);
    event RewardPaid(bytes32 indexed childId, bytes32 indexed taskId, uint256 amount);
    event Spent(bytes32 indexed childId, bytes32 indexed conceptId, uint256 amount, uint256 nonce);

    error NotParent();
    error InvalidPublicKey();
    error UnknownChild(bytes32 childId);
    error ZeroAmount();
    error TaskAlreadyRewarded(bytes32 taskId);
    error InvalidSignature();
    error InsufficientBalance(uint256 available, uint256 requested);

    modifier onlyParent() {
        if (msg.sender != parent) revert NotParent();
        _;
    }

    constructor() {
        parent = msg.sender;
    }

    /// @notice El padre vincula el móvil del hijo (su clave pública). Si el móvil se pierde,
    ///         el padre registra el nuevo: la "recuperación de cuenta" es natural en una familia.
    function registerDevice(bytes32 childId, bytes32 x, bytes32 y) external onlyParent {
        if (!P256.isValidPublicKey(x, y)) revert InvalidPublicKey();
        deviceKeyOf[childId] = DeviceKey(x, y);
        emit DeviceRegistered(childId, x, y);
    }

    function reward(bytes32 childId, bytes32 taskId, uint256 amount) external onlyParent {
        _requireKnownChild(childId);
        if (amount == 0) revert ZeroAmount();
        if (taskRewarded[taskId]) revert TaskAlreadyRewarded(taskId);
        taskRewarded[taskId] = true;
        balanceOf[childId] += amount;
        emit RewardPaid(childId, taskId, amount);
    }

    /// @notice Lo que el móvil del hijo firma. Incluye chainid y la dirección del contrato para que la
    ///         firma no sirva en otra red ni en otro contrato, y el nonce para que no se pueda repetir.
    /// @dev sha256 (no keccak256) porque es lo que aplica "SHA256withECDSA" en Android/Java.
    function spendDigest(bytes32 childId, bytes32 conceptId, uint256 amount, uint256 nonce)
        public
        view
        returns (bytes32)
    {
        return sha256(abi.encode(block.chainid, address(this), childId, conceptId, amount, nonce));
    }

    /// @notice Cualquiera puede enviar la operación (normalmente el backend, que paga el gas),
    ///         pero solo se ejecuta si la firmó el dispositivo registrado del hijo.
    function spendSigned(bytes32 childId, bytes32 conceptId, uint256 amount, bytes32 r, bytes32 s) external {
        DeviceKey memory key = _requireKnownChild(childId);
        if (amount == 0) revert ZeroAmount();

        uint256 nonce = nonceOf[childId];
        bytes32 digest = spendDigest(childId, conceptId, amount, nonce);
        if (!P256.verifyNative(digest, r, s, key.x, key.y)) revert InvalidSignature();

        uint256 available = balanceOf[childId];
        if (available < amount) revert InsufficientBalance(available, amount);

        nonceOf[childId] = nonce + 1;
        balanceOf[childId] = available - amount;
        emit Spent(childId, conceptId, amount, nonce);
    }

    function _requireKnownChild(bytes32 childId) private view returns (DeviceKey memory key) {
        key = deviceKeyOf[childId];
        if (key.x == bytes32(0) && key.y == bytes32(0)) revert UnknownChild(childId);
    }
}
