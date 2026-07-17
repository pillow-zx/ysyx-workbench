package npc.core

import chisel3._
import chisel3.util._
import npc.interface.Message
import npc.unit.{Alu, BranchUnit}
import npc.common._

class EXU(xlen: Int) extends Module {
  val io = IO(new Bundle {
    val in:  DecoupledIO[Message] = Flipped(Decoupled(new Message(xlen)))
    val out: DecoupledIO[Message] = Decoupled(new Message(xlen))
    val epc: UInt                 = Input(UInt(xlen.W))
  })

  private val alu:        Alu        = Module(new Alu(xlen))
  private val branchUnit: BranchUnit = Module(new BranchUnit(xlen))

  private val pc:      UInt = io.in.bits.pc
  private val imm:     UInt = io.in.bits.decode.imm
  private val aluOp:   UInt = io.in.bits.decode.alu.op
  private val aluSrc1: UInt = io.in.bits.decode.alu.src1
  private val aluSrc2: UInt = io.in.bits.decode.alu.src2
  private val rdata1:  UInt = io.in.bits.rs1Data
  private val rdata2:  UInt = io.in.bits.rs2Data

  private val pcSel:       UInt = io.in.bits.decode.pc.sel
  private val branchValid: Bool = io.in.bits.decode.branch.valid

  private val operand1: UInt = MuxLookup(
    aluSrc1,
    0.U(xlen.W)
  )(
    Seq(
      AluSrc1.Rs1  -> rdata1,
      AluSrc1.Pc   -> pc,
      AluSrc1.Zero -> 0.U(xlen.W)
    )
  )

  private val operand2: UInt = MuxLookup(
    aluSrc2,
    0.U(xlen.W)
  )(
    Seq(
      AluSrc2.Rs2  -> rdata2,
      AluSrc2.Imm  -> imm,
      AluSrc2.Four -> 4.U(xlen.W)
    )
  )

  alu.io.op          := aluOp
  alu.io.src1        := operand1
  alu.io.src2        := operand2
  branchUnit.io.src1 := rdata1
  branchUnit.io.src2 := rdata2
  branchUnit.io.op   := io.in.bits.decode.branch.op
  private val aluResult: UInt = alu.io.out

  private val pcPlus4:         UInt = pc + 4.U
  private val branchTarget:    UInt = pc + imm
  private val jalrTarget:      UInt = (rdata1 + imm) & (~1.U(xlen.W))
  private val branchTaken:     Bool = branchValid && branchUnit.io.taken
  private val controlTransfre: Bool = branchTaken ||
    (pcSel === PcSel.Jal) || (pcSel === PcSel.Jalr) || (pcSel === PcSel.Epc)

  private val selectedNextPc: UInt = MuxLookup(
    pcSel,
    pcPlus4
  )(
    Seq(
      PcSel.Plus4  -> pcPlus4,
      PcSel.Branch -> Mux(branchTaken, branchTarget, pcPlus4),
      PcSel.Jal    -> branchTarget,
      PcSel.Jalr   -> jalrTarget,
      PcSel.Hold   -> pc,
      PcSel.Epc    -> io.epc
    )
  )

  private val instructionMisaligned: Bool = controlTransfre && selectedNextPc(1, 0).orR

  private val next: Message = WireDefault(io.in.bits)
  next.selectedNextPc := selectedNextPc
  next.aluResult      := aluResult

  when(instructionMisaligned) {
    next.exception.valid := true.B
    next.exception.cause := TrapCause.InstAddrMisaligned
  }

  io.out.bits  := next
  io.out.valid := io.in.valid
  io.in.ready  := io.out.ready
}
