package npc.unit

import chisel3._
import npc.common.{AluOp, AluSrc1, AluSrc2, BranchOp, CsrCmd, CsrSrc, MemSize, PcSel, WbSel}
import npc.core.Instructions
import npc.interface.DecodeResult

class Decode(xlen: Int) extends Module {
  val io: DecodeIO = IO(new DecodeIO(xlen))

  private val result: DecodeResult = WireDefault(0.U.asTypeOf(new DecodeResult(xlen)))
  private val immSel: UInt         = WireDefault(ImmSel.I)

  private val immGen: ImmGen = Module(new ImmGen(xlen))
  immGen.io.inst := io.inst
  immGen.io.sel  := immSel

  result.rs1 := io.inst(19, 15)
  result.rs2 := io.inst(24, 20)
  result.rd  := io.inst(11, 7)
  result.imm := immGen.io.imm

  result.pc.sel         := PcSel.Plus4
  result.alu.op         := AluOp.Add
  result.alu.src1       := AluSrc1.Rs1
  result.alu.src2       := AluSrc2.Rs2
  result.branch.op      := BranchOp.Eq
  result.mem.size       := MemSize.Word
  result.wb.sel         := WbSel.Alu
  result.csr.addr       := io.inst(31, 20)
  result.csr.cmd        := CsrCmd.None
  result.csr.src        := CsrSrc.Rs1
  result.system.illegal := true.B

  private def markLegal(): Unit = {
    result.valid          := true.B
    result.system.illegal := false.B
  }

  private def decodeAlu(op: UInt): Unit = {
    markLegal()
    result.alu.op := op
    result.wb.wen := true.B
  }

  private def decodeAluImm(op: UInt): Unit = {
    decodeAlu(op)
    immSel          := ImmSel.I
    result.alu.src2 := AluSrc2.Imm
  }

  private def decodeUpper(op: UInt, src1: UInt): Unit = {
    markLegal()
    immSel          := ImmSel.U
    result.alu.op   := op
    result.alu.src1 := src1
    result.alu.src2 := AluSrc2.Imm
    result.wb.wen   := true.B
  }

  private def decodeJump(pcSel: UInt, immFormat: UInt, src1: UInt): Unit = {
    markLegal()
    immSel          := immFormat
    result.pc.sel   := pcSel
    result.alu.op   := AluOp.Add
    result.alu.src1 := src1
    result.alu.src2 := AluSrc2.Imm
    result.wb.wen   := true.B
    result.wb.sel   := WbSel.Pc4
  }

  private def decodeBranch(op: UInt): Unit = {
    markLegal()
    immSel              := ImmSel.B
    result.pc.sel       := PcSel.Branch
    result.alu.op       := AluOp.Add
    result.alu.src1     := AluSrc1.Pc
    result.alu.src2     := AluSrc2.Imm
    result.branch.valid := true.B
    result.branch.op    := op
  }

  private def decodeLoad(size: UInt, unsigned: Bool): Unit = {
    markLegal()
    immSel              := ImmSel.I
    result.alu.src2     := AluSrc2.Imm
    result.mem.valid    := true.B
    result.mem.size     := size
    result.mem.unsigned := unsigned
    result.wb.wen       := true.B
    result.wb.sel       := WbSel.Mem
  }

  private def decodeStore(size: UInt): Unit = {
    markLegal()
    immSel             := ImmSel.S
    result.alu.src2    := AluSrc2.Imm
    result.mem.valid   := true.B
    result.mem.isStore := true.B
    result.mem.size    := size
  }

  private def decodeCsr(cmd: UInt, src: UInt, wen: Bool): Unit = {
    markLegal()
    result.csr.valid := true.B
    result.csr.wen   := wen
    result.csr.cmd   := cmd
    result.csr.src   := src
    result.wb.wen    := true.B
    result.wb.sel    := WbSel.Csr

    when(src === CsrSrc.Imm) {
      immSel := ImmSel.Z
    }
  }

  private def decodeUnimplemented(): Unit = {
    result.valid          := false.B
    result.system.illegal := true.B
  }

  private def decodeFence(): Unit = {
    markLegal()
  }

  private def decodeEcall(): Unit = {
    markLegal()
    result.pc.sel       := PcSel.Trap
    result.system.ecall := true.B
  }

  private def decodeEbreak(): Unit = {
    markLegal()
    result.system.ebreak := true.B
  }

  private def decodeMret(): Unit = {
    markLegal()
    result.pc.sel      := PcSel.Epc
    result.system.mret := true.B
  }

  when(Instructions.LUI === io.inst) {
    decodeUpper(AluOp.PassThroughSrc2, AluSrc1.Zero)
  }.elsewhen(Instructions.AUIPC === io.inst) {
    decodeUpper(AluOp.Add, AluSrc1.Pc)
  }.elsewhen(Instructions.JAL === io.inst) {
    decodeJump(PcSel.Jal, ImmSel.J, AluSrc1.Pc)
  }.elsewhen(Instructions.JALR === io.inst) {
    decodeJump(PcSel.Jalr, ImmSel.I, AluSrc1.Rs1)
  }.elsewhen(Instructions.BEQ === io.inst) {
    decodeBranch(BranchOp.Eq)
  }.elsewhen(Instructions.BNE === io.inst) {
    decodeBranch(BranchOp.Ne)
  }.elsewhen(Instructions.BLT === io.inst) {
    decodeBranch(BranchOp.Lt)
  }.elsewhen(Instructions.BGE === io.inst) {
    decodeBranch(BranchOp.Ge)
  }.elsewhen(Instructions.BLTU === io.inst) {
    decodeBranch(BranchOp.Ltu)
  }.elsewhen(Instructions.BGEU === io.inst) {
    decodeBranch(BranchOp.Geu)
  }.elsewhen(Instructions.LB === io.inst) {
    decodeLoad(MemSize.Byte, false.B)
  }.elsewhen(Instructions.LH === io.inst) {
    decodeLoad(MemSize.Half, false.B)
  }.elsewhen(Instructions.LW === io.inst) {
    decodeLoad(MemSize.Word, false.B)
  }.elsewhen(Instructions.LBU === io.inst) {
    decodeLoad(MemSize.Byte, true.B)
  }.elsewhen(Instructions.LHU === io.inst) {
    decodeLoad(MemSize.Half, true.B)
  }.elsewhen(Instructions.SB === io.inst) {
    decodeStore(MemSize.Byte)
  }.elsewhen(Instructions.SH === io.inst) {
    decodeStore(MemSize.Half)
  }.elsewhen(Instructions.SW === io.inst) {
    decodeStore(MemSize.Word)
  }.elsewhen(Instructions.ADDI === io.inst) {
    decodeAluImm(AluOp.Add)
  }.elsewhen(Instructions.SLTI === io.inst) {
    decodeAluImm(AluOp.LessThanSigned)
  }.elsewhen(Instructions.SLTIU === io.inst) {
    decodeAluImm(AluOp.LessThanUnsigned)
  }.elsewhen(Instructions.XORI === io.inst) {
    decodeAluImm(AluOp.BitwiseXor)
  }.elsewhen(Instructions.ORI === io.inst) {
    decodeAluImm(AluOp.BitwiseOr)
  }.elsewhen(Instructions.ANDI === io.inst) {
    decodeAluImm(AluOp.BitwiseAnd)
  }.elsewhen(Instructions.SLLI === io.inst) {
    decodeAluImm(AluOp.ShiftLeftLogical)
  }.elsewhen(Instructions.SRLI === io.inst) {
    decodeAluImm(AluOp.ShiftRightLogical)
  }.elsewhen(Instructions.SRAI === io.inst) {
    decodeAluImm(AluOp.ShiftRightArithmetic)
  }.elsewhen(Instructions.ADD === io.inst) {
    decodeAlu(AluOp.Add)
  }.elsewhen(Instructions.SUB === io.inst) {
    decodeAlu(AluOp.Subtract)
  }.elsewhen(Instructions.SLL === io.inst) {
    decodeAlu(AluOp.ShiftLeftLogical)
  }.elsewhen(Instructions.SLT === io.inst) {
    decodeAlu(AluOp.LessThanSigned)
  }.elsewhen(Instructions.SLTU === io.inst) {
    decodeAlu(AluOp.LessThanUnsigned)
  }.elsewhen(Instructions.XOR === io.inst) {
    decodeAlu(AluOp.BitwiseXor)
  }.elsewhen(Instructions.SRL === io.inst) {
    decodeAlu(AluOp.ShiftRightLogical)
  }.elsewhen(Instructions.SRA === io.inst) {
    decodeAlu(AluOp.ShiftRightArithmetic)
  }.elsewhen(Instructions.OR === io.inst) {
    decodeAlu(AluOp.BitwiseOr)
  }.elsewhen(Instructions.AND === io.inst) {
    decodeAlu(AluOp.BitwiseAnd)
  }.elsewhen(Instructions.CSRRW === io.inst) {
    decodeCsr(CsrCmd.Write, CsrSrc.Rs1, true.B)
  }.elsewhen(Instructions.CSRRS === io.inst) {
    decodeCsr(CsrCmd.Set, CsrSrc.Rs1, io.inst(19, 15).orR)
  }.elsewhen(Instructions.CSRRC === io.inst) {
    decodeCsr(CsrCmd.Clear, CsrSrc.Rs1, io.inst(19, 15).orR)
  }.elsewhen(Instructions.CSRRWI === io.inst) {
    decodeCsr(CsrCmd.Write, CsrSrc.Imm, true.B)
  }.elsewhen(Instructions.CSRRSI === io.inst) {
    decodeCsr(CsrCmd.Set, CsrSrc.Imm, io.inst(19, 15).orR)
  }.elsewhen(Instructions.CSRRCI === io.inst) {
    decodeCsr(CsrCmd.Clear, CsrSrc.Imm, io.inst(19, 15).orR)
  }.elsewhen(Instructions.FENCE === io.inst) {
    decodeFence()
  }.elsewhen(Instructions.FENCEI === io.inst) {
    decodeFence()
  }.elsewhen(Instructions.ECALL === io.inst) {
    decodeEcall()
  }.elsewhen(Instructions.EBREAK === io.inst) {
    decodeEbreak()
  }.elsewhen(Instructions.MRET === io.inst) {
    decodeMret()
  }.elsewhen(Instructions.SRET === io.inst) {
    decodeUnimplemented()
  }.elsewhen(Instructions.WFI === io.inst) {
    decodeUnimplemented()
  }.elsewhen(Instructions.SFENCEVMA === io.inst) {
    decodeUnimplemented()
  }

  io.result := result
}
