package npc.common

case class NpcConfig(
  xlen:        Int = 32,
  resetVector: BigInt = BigInt("80000000", 16),
  memoryBase:  BigInt = BigInt("80000000", 16),
  mmioBase:    BigInt = BigInt("a0000000", 16))
