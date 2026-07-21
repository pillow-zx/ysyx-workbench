package npc.common

import chisel3._

object PcSel {
  val Width = 3

  val Plus4:  UInt = 0.U(Width.W)
  val Branch: UInt = 1.U(Width.W)
  val Jal:    UInt = 2.U(Width.W)
  val Jalr:   UInt = 3.U(Width.W)
  val Hold:   UInt = 4.U(Width.W)
  val Trap:   UInt = 5.U(Width.W)
  val Epc:    UInt = 6.U(Width.W)
}

object AluOp {
  val Width = 4

  val Add:                  UInt = 0.U(Width.W)
  val Subtract:             UInt = 1.U(Width.W)
  val ShiftLeftLogical:     UInt = 2.U(Width.W)
  val LessThanSigned:       UInt = 3.U(Width.W)
  val LessThanUnsigned:     UInt = 4.U(Width.W)
  val BitwiseAnd:           UInt = 5.U(Width.W)
  val BitwiseOr:            UInt = 6.U(Width.W)
  val BitwiseXor:           UInt = 7.U(Width.W)
  val ShiftRightLogical:    UInt = 8.U(Width.W)
  val ShiftRightArithmetic: UInt = 9.U(Width.W)
  val PassThroughSrc1:      UInt = 10.U(Width.W)
  val PassThroughSrc2:      UInt = 11.U(Width.W)
}

object AluSrc1 {
  val Width = 2

  val Rs1:  UInt = 0.U(Width.W)
  val Pc:   UInt = 1.U(Width.W)
  val Zero: UInt = 2.U(Width.W)
}

object AluSrc2 {
  val Width = 2

  val Rs2:  UInt = 0.U(Width.W)
  val Imm:  UInt = 1.U(Width.W)
  val Four: UInt = 2.U(Width.W)
}

object BranchOp {
  val Width = 3

  val Eq:  UInt = 0.U(Width.W)
  val Ne:  UInt = 1.U(Width.W)
  val Lt:  UInt = 2.U(Width.W)
  val Ge:  UInt = 3.U(Width.W)
  val Ltu: UInt = 4.U(Width.W)
  val Geu: UInt = 5.U(Width.W)
}

object MemSize {
  val Width = 2

  val Byte:  UInt = 0.U(Width.W)
  val Half:  UInt = 1.U(Width.W)
  val Word:  UInt = 2.U(Width.W)
  val Dword: UInt = 3.U(Width.W)
}

object WbSel {
  val Width = 3

  val Alu: UInt = 0.U(Width.W)
  val Mem: UInt = 1.U(Width.W)
  val Pc4: UInt = 2.U(Width.W)
  val Csr: UInt = 3.U(Width.W)
}

object CsrCmd {
  val Width = 3

  val None:  UInt = 0.U(Width.W)
  val Write: UInt = 1.U(Width.W)
  val Set:   UInt = 2.U(Width.W)
  val Clear: UInt = 3.U(Width.W)
}

object CsrSrc {
  val Width = 1

  val Rs1: UInt = 0.U(Width.W)
  val Imm: UInt = 1.U(Width.W)
}

object CsrAddr {
  val Width: Int = Constants.CsrAddrWidth

  val Mstatus: UInt = 0x300.U(Width.W)
  val Mtvec:   UInt = 0x305.U(Width.W)
  val Mepc:    UInt = 0x341.U(Width.W)
  val Mcause:  UInt = 0x342.U(Width.W)
}

object TrapCause {
  val Width = 32

  val InstAddrMisaligned:  UInt = 0.U(Width.W)
  val InstAccessFault:     UInt = 1.U(Width.W)
  val IllegalInst:         UInt = 2.U(Width.W)
  val LoadAddrMisaligned:  UInt = 4.U(Width.W)
  val LoadAccessFault:     UInt = 5.U(Width.W)
  val StoreAddrMisaligned: UInt = 6.U(Width.W)
  val StoreAccessFault:    UInt = 7.U(Width.W)
  val EcallM:              UInt = 11.U(Width.W)
}
