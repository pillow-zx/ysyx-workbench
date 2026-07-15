package npc.unit

import chisel3._
import npc.common.{AluOp, BranchOp, Constants, CsrAddr, CsrCmd}
import npc.interface.DecodeResult

class AluIO(xlen: Int) extends Bundle {
  val src1: UInt = Input(UInt(xlen.W))
  val src2: UInt = Input(UInt(xlen.W))
  val op:   UInt = Input(UInt(AluOp.Width.W))
  val out:  UInt = Output(UInt(xlen.W))
}

class BranchUnitIO(xlen: Int) extends Bundle {
  val src1:  UInt = Input(UInt(xlen.W))
  val src2:  UInt = Input(UInt(xlen.W))
  val op:    UInt = Input(UInt(BranchOp.Width.W))
  val taken: Bool = Output(Bool())
}

class ImmGenIO(xlen: Int) extends Bundle {
  val inst: UInt = Input(UInt(Constants.InstWidth.W))
  val sel:  UInt = Input(UInt(ImmSel.Width.W))
  var imm:  UInt = Output(UInt(xlen.W))
}

class RegFileIO(xlen: Int) extends Bundle {
  val raddr1: UInt = Input(UInt(Constants.RegAddrWidth.W))
  val raddr2: UInt = Input(UInt(Constants.RegAddrWidth.W))
  val rdata1: UInt = Output(UInt(xlen.W))
  val rdata2: UInt = Output(UInt(xlen.W))

  val wen:   Bool = Input(Bool())
  val waddr: UInt = Input(UInt(Constants.RegAddrWidth.W))
  val wdata: UInt = Input(UInt(xlen.W))

  val debugGpr: Vec[UInt] = Output(Vec(Constants.RegCount, UInt(xlen.W)))
}

class CsrTrapRequest(xlen: Int) extends Bundle {
  val valid = Bool()
  val pc    = UInt(xlen.W)
  val cause = UInt(xlen.W)
}

class CsrIO(xlen: Int) extends Bundle {
  val addr:  UInt = Input(UInt(CsrAddr.Width.W))
  val cmd:   UInt = Input(UInt(CsrCmd.Width.W))
  val src:   UInt = Input(UInt(xlen.W))
  val wen:   Bool = Input(Bool())
  val rdata: UInt = Output(UInt(xlen.W))

  val trapReq:    CsrTrapRequest = Input(new CsrTrapRequest(xlen))
  val trapVector: UInt           = Output(UInt(xlen.W))
  val epc:        UInt           = Output(UInt(xlen.W))
}

class DecodeIO(xlen: Int) extends Bundle {
  val inst:   UInt         = Input(UInt(Constants.InstWidth.W))
  val result: DecodeResult = Output(new DecodeResult(xlen))
}
