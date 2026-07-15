package npc.interface

import chisel3._
import npc.common.{AluOp, AluSrc1, AluSrc2, BranchOp, Constants, CsrAddr, CsrCmd, CsrSrc, MemSize, PcSel, WbSel}

class PcCtrl extends Bundle {
  val sel = UInt(PcSel.Width.W)
}

class AluCtrl extends Bundle {
  val op   = UInt(AluOp.Width.W)
  val src1 = UInt(AluSrc1.Width.W)
  val src2 = UInt(AluSrc2.Width.W)
}

class BranchCtrl extends Bundle {
  val valid = Bool()
  val op    = UInt(BranchOp.Width.W)
}

class MemCtrl extends Bundle {
  val valid    = Bool()
  val isStore  = Bool()
  val size     = UInt(MemSize.Width.W)
  val unsigned = Bool()
}

class WbCtrl extends Bundle {
  val wen = Bool()
  val sel = UInt(WbSel.Width.W)
}

class CsrCtrl extends Bundle {
  val valid = Bool()
  val wen   = Bool()
  val cmd   = UInt(CsrCmd.Width.W)
  val addr  = UInt(CsrAddr.Width.W)
  val src   = UInt(CsrSrc.Width.W)
}

class SystemCtrl extends Bundle {
  val illegal = Bool()
  val ecall   = Bool()
  val ebreak  = Bool()
  val mret    = Bool()
}

class DecodeResult(xlen: Int) extends Bundle {
  val valid = Bool()

  val rs1 = UInt(Constants.RegAddrWidth.W)
  val rs2 = UInt(Constants.RegAddrWidth.W)
  val rd  = UInt(Constants.RegAddrWidth.W)
  val imm = UInt(xlen.W)

  val pc     = new PcCtrl
  val alu    = new AluCtrl
  val branch = new BranchCtrl
  val mem    = new MemCtrl
  val wb     = new WbCtrl
  val csr    = new CsrCtrl
  val system = new SystemCtrl
}
