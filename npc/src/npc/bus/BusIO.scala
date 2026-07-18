package npc.bus

import chisel3._
import chisel3.util._

class SimpleBusReq(xlen: Int) extends Bundle {
  val addr  = UInt(xlen.W)
  val wen   = Bool()
  val wdata = UInt(xlen.W)
  val wmask = UInt((xlen / 8).W)
}

class SimpleBusResp(xlen: Int) extends Bundle {
  val rdata = UInt(xlen.W)
  val error = Bool()
}

class SimpleBusMasterIO(xlen: Int) extends Bundle {
  val req:  DecoupledIO[SimpleBusReq]  = Decoupled(new SimpleBusReq(xlen))
  val resp: DecoupledIO[SimpleBusResp] = Flipped(Decoupled(new SimpleBusResp(xlen)))
}
