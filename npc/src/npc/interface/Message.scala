package npc.interface

import chisel3._

class ExceptionInfo(xlen: Int) extends Bundle {
  val valid = Bool()
  val cause = UInt(xlen.W)
}

class Message(xlen: Int) extends Bundle {
  val pc             = UInt(xlen.W)
  val inst           = UInt(xlen.W)
  val selectedNextPc = UInt(xlen.W)

  val decode = new DecodeResult(xlen)

  val rs1Data = UInt(xlen.W)
  val rs2Data = UInt(xlen.W)

  val aluResult = UInt(xlen.W)

  val memData = UInt(xlen.W)

  val exception = new ExceptionInfo(xlen)
}
