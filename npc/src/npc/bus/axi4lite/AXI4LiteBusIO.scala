package npc.bus.axi4lite

import chisel3._
import chisel3.util._

class Axi4LiteAW(addrWidth: Int) extends Bundle {
  val addr = UInt(addrWidth.W)
  val prot = UInt(3.W) // not use
}

class Axi4LiteW(addrWidth: Int, dataWidth: Int) extends Bundle {
  val data = UInt(dataWidth.W)
  val strb = UInt((dataWidth / 8).W)
}

class Axi4LiteB extends Bundle {
  val resp = UInt(2.W) // 2 bits default 0/2/3 bits can be choosen
}

class Axi4LiteAR(addrWidth: Int) extends Bundle {
  val addr = UInt(addrWidth.W)
  val prot = UInt(3.W) // not use
}

class Axi4LiteR(dataWidth: Int) extends Bundle {
  val data = UInt(dataWidth.W)
  val resp = UInt(2.W)
}

class Axi4LiteMasterIO(addrWidth: Int, dataWidth: Int) extends Bundle {
  val aw: DecoupledIO[Axi4LiteAW] = Decoupled(new Axi4LiteAW(addrWidth))
  val w:  DecoupledIO[Axi4LiteW]  = Decoupled(new Axi4LiteW(addrWidth, dataWidth))
  val ar: DecoupledIO[Axi4LiteAR] = Decoupled(new Axi4LiteAR(addrWidth))
  val b:  DecoupledIO[Axi4LiteB]  = Flipped(Decoupled(new Axi4LiteB))
  val r:  DecoupledIO[Axi4LiteR]  = Flipped(Decoupled(new Axi4LiteR(dataWidth)))
}

class Axi4LiteSlaveIO(addrWidth: Int, dataWidth: Int) extends Bundle {
  val aw: DecoupledIO[Axi4LiteAW] = Flipped(Decoupled(new Axi4LiteAW(addrWidth)))
  val w:  DecoupledIO[Axi4LiteW]  = Flipped(Decoupled(new Axi4LiteW(addrWidth, dataWidth)))
  val b:  DecoupledIO[Axi4LiteB]  = Decoupled(new Axi4LiteB)
  val ar: DecoupledIO[Axi4LiteAR] = Flipped(Decoupled(new Axi4LiteAR(addrWidth)))
  val r:  DecoupledIO[Axi4LiteR]  = Decoupled(new Axi4LiteR(dataWidth))
}
