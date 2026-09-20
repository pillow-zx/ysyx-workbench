package npc.common

case class NpcConfig(
  xlen:        Int = 32,
  resetVector: BigInt = BigInt("20000000", 16))
