import { parseAbi } from "viem";

// ABI mínima de contracts/FamilyLedger.sol (misma firma que en el proyecto Hardhat).
export const familyLedgerAbi = parseAbi([
  "function parent() view returns (address)",
  "function isChild(address) view returns (bool)",
  "function balanceOf(address) view returns (uint256)",
  "function taskRewarded(bytes32) view returns (bool)",
  "function addChild(address child)",
  "function reward(address child, bytes32 taskId, uint256 amount)",
  "function spend(bytes32 conceptId, uint256 amount)",
  "event ChildAdded(address indexed child)",
  "event RewardPaid(address indexed child, bytes32 indexed taskId, uint256 amount)",
  "event Spent(address indexed child, bytes32 indexed conceptId, uint256 amount)",
  "error NotParent()",
  "error NotChild()",
  "error ZeroAddress()",
  "error ZeroAmount()",
  "error AlreadyChild(address child)",
  "error TaskAlreadyRewarded(bytes32 taskId)",
  "error InsufficientBalance(uint256 available, uint256 requested)",
]);
