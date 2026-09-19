package npc.memory

import chisel3._

case class AddressRegion(base: BigInt, size: BigInt) {
  require(size > 0, "address region size must be positive")

  def contains(address: UInt): Bool = address >= base.U && address < (base + size).U
}

object SimulationAddressMap {
  val Clint: AddressRegion = AddressRegion(BigInt("a0000048", 16), 8)
}
