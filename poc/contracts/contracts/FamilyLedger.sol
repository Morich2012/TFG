// SPDX-License-Identifier: MIT
pragma solidity ^0.8.28;

/// @title FamilyLedger (PoC)
/// @notice Libro de recompensas de una familia. Los saldos son unidades internas
///         del contrato, sin valor económico: no es un token ERC-20 ni mueve ETH.
/// @dev Solo se guardan direcciones e identificadores opacos (hashes). Ningún dato
///      personal (nombres, descripción de tareas...) va on-chain: eso vive en PostgreSQL.
contract FamilyLedger {
    address public immutable parent;

    mapping(address => bool) public isChild;
    mapping(address => uint256) public balanceOf;
    /// @notice taskId => ya recompensada. Evita pagar dos veces la misma tarea.
    mapping(bytes32 => bool) public taskRewarded;

    event ChildAdded(address indexed child);
    event RewardPaid(address indexed child, bytes32 indexed taskId, uint256 amount);
    event Spent(address indexed child, bytes32 indexed conceptId, uint256 amount);

    error NotParent();
    error NotChild();
    error ZeroAddress();
    error ZeroAmount();
    error AlreadyChild(address child);
    error TaskAlreadyRewarded(bytes32 taskId);
    error InsufficientBalance(uint256 available, uint256 requested);

    modifier onlyParent() {
        if (msg.sender != parent) revert NotParent();
        _;
    }

    constructor() {
        parent = msg.sender;
    }

    function addChild(address child) external onlyParent {
        if (child == address(0)) revert ZeroAddress();
        if (isChild[child]) revert AlreadyChild(child);
        isChild[child] = true;
        emit ChildAdded(child);
    }

    /// @param taskId hash de un identificador de tarea que vive fuera de la cadena
    function reward(address child, bytes32 taskId, uint256 amount) external onlyParent {
        if (!isChild[child]) revert NotChild();
        if (amount == 0) revert ZeroAmount();
        if (taskRewarded[taskId]) revert TaskAlreadyRewarded(taskId);

        taskRewarded[taskId] = true;
        balanceOf[child] += amount;
        emit RewardPaid(child, taskId, amount);
    }

    /// @notice El hijo registra un gasto desde su propia cuenta.
    function spend(bytes32 conceptId, uint256 amount) external {
        if (!isChild[msg.sender]) revert NotChild();
        if (amount == 0) revert ZeroAmount();
        uint256 available = balanceOf[msg.sender];
        if (available < amount) revert InsufficientBalance(available, amount);

        balanceOf[msg.sender] = available - amount;
        emit Spent(msg.sender, conceptId, amount);
    }
}
