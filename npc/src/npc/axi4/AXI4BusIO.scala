package npc.axi4

import chisel3._
import chisel3.util._

class AXI4Address(addrWidth: Int, idWidth: Int) extends Bundle {
  val addr:  UInt = UInt(addrWidth.W)
  val id:    UInt = UInt(idWidth.W)
  val len:   UInt = UInt(8.W)
  val size:  UInt = UInt(3.W)
  val burst: UInt = UInt(2.W)
}

class AXI4WriteData(dataWidth: Int) extends Bundle {
  val data: UInt = UInt(dataWidth.W)
  val strb: UInt = UInt((dataWidth / 8).W)
  val last: Bool = Bool()
}

class AXI4WriteResponse(idWidth: Int) extends Bundle {
  val resp: UInt = UInt(2.W)
  val id:   UInt = UInt(idWidth.W)
}

class AXI4ReadData(dataWidth: Int, idWidth: Int) extends Bundle {
  val data: UInt = UInt(dataWidth.W)
  val resp: UInt = UInt(2.W)
  val last: Bool = Bool()
  val id:   UInt = UInt(idWidth.W)
}

class AXI4IO(addrWidth: Int, dataWidth: Int, idWidth: Int = 4) extends Bundle {
  val aw: DecoupledIO[AXI4Address]       = Decoupled(new AXI4Address(addrWidth, idWidth))
  val w:  DecoupledIO[AXI4WriteData]     = Decoupled(new AXI4WriteData(dataWidth))
  val b:  DecoupledIO[AXI4WriteResponse] = Flipped(Decoupled(new AXI4WriteResponse(idWidth)))
  val ar: DecoupledIO[AXI4Address]       = Decoupled(new AXI4Address(addrWidth, idWidth))
  val r:  DecoupledIO[AXI4ReadData]      = Flipped(Decoupled(new AXI4ReadData(dataWidth, idWidth)))
}
