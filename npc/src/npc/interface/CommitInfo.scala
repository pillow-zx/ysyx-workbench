package npc.interface

import chisel3._
import npc.common.Constants

class CommitInfo(xlen: Int) extends Bundle {
  val valid  = Bool()
  val pc     = UInt(xlen.W)
  val inst   = UInt(Constants.InstWidth.W)
  val nextPc = UInt(xlen.W)

  val rd      = UInt(Constants.RegAddrWidth.W)
  val rfWen   = Bool()
  val rfWdata = UInt(xlen.W)

  val trap         = Bool()
  val trapCause    = UInt(xlen.W)
  val isEbreak     = Bool()
  val skipDifftest = Bool()
}

class DebugInfo(xlen: Int) extends Bundle {
  val pc  = UInt(xlen.W)
  val gpr = Vec(Constants.RegCount, UInt(xlen.W))
}
